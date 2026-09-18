/**
 * 客服聊天 WebSocket 客户端
 *
 * 这是本项目**唯一不走 HTTP 的交互** —— 后端是独立的 Netty 进程（默认 9090），
 * 不是 Spring 的 8080，所以它不受 vite 代理影响，必须直连。
 *
 * 协议（详见 docx/netty-tutorial.md）：
 *   连  接：ws://host:9090/ws/chat?token=<JWT>      ← 浏览器无法自定义请求头，token 只能放 URL
 *   发送：  {"type":"chat","to":9,"content":"你好","clientMsgId":"uuid"}
 *          {"type":"ping"}
 *   接收：  {"type":"chat","from":9,"content":"...","time":"..."}
 *          {"type":"history","list":[...]}          ← 上线时补发的离线消息
 *          {"type":"system"|"error","content":"..."}
 *          {"type":"pong"}
 *
 * 服务端规则（不遵守会被断开）：
 *   · 90 秒内没有任何来往 → 服务端主动 close（所以必须发心跳）
 *   · 心跳间隔必须明显小于 90 秒，这里用 30 秒
 */

import { getToken } from '@/utils/auth'

/** 聊天服务地址：部署时用 VITE_WS_BASE 覆盖（上 HTTPS 后必须是 wss://） */
const WS_BASE = (import.meta.env.VITE_WS_BASE as string | undefined) ?? 'ws://localhost:9090'
const WS_PATH = '/ws/chat'

/** 心跳间隔：30 秒 × 3 = 90 秒，正好是服务端的空闲超时 */
const HEARTBEAT_MS = 30_000

/** 自动重连的最大次数与退避上限 */
const MAX_RETRY = 5
const MAX_RETRY_DELAY_MS = 15_000

// ==================== 协议类型 ====================

export type ChatStatus = 'connecting' | 'online' | 'offline'

/** 一条聊天消息（收发都是这个结构，靠 from 判断左右） */
export interface ChatMessagePayload {
  type: 'chat'
  from: number
  to: number
  /** 发送方是不是客服：true=客服发的 */
  fromAdmin: boolean
  content: string
  /** 服务端生成的 "yyyy-MM-dd HH:mm:ss" */
  time: string
  /** 客户端消息ID（自己发的消息会原样带回，可用于去重/回显配对） */
  clientMsgId?: string
}

/** 上线时补发的离线消息 */
export interface ChatHistoryPayload {
  type: 'history'
  list: Array<Omit<ChatMessagePayload, 'type'>>
}

/** 系统提示 / 错误提示 */
export interface ChatTipPayload {
  type: 'system' | 'error'
  content: string
}

/** 心跳回执 */
export interface ChatPongPayload {
  type: 'pong'
}

export type ChatInbound = ChatMessagePayload | ChatHistoryPayload | ChatTipPayload | ChatPongPayload

export interface ChatClientOptions {
  /** 客服的 userId（收到对方消息时用于判断归属；本项目约定客服是 9） */
  toUserId: number
  /** 收到任意服务端消息时回调 */
  onMessage?: (msg: ChatInbound) => void
  /** 连接状态变化时回调（用来切换界面上的在线/离线小圆点） */
  onStatusChange?: (status: ChatStatus) => void
  /** 是否自动重连，默认 true */
  autoReconnect?: boolean
}

// ==================== 客户端实现 ====================

export class ChatClient {
  /** 当前登录用户的 id，从 JWT 的 sub 里取；用来判断消息显示在左侧还是右侧 */
  readonly myUserId: number | null

  private ws: WebSocket | null = null
  private heartbeatTimer: number | undefined
  private retryTimer: number | undefined
  private retryCount = 0
  /** 用户主动关闭时不再重连 */
  private closedByUser = false

  private readonly options: Required<Pick<ChatClientOptions, 'autoReconnect'>> & ChatClientOptions

  constructor(options: ChatClientOptions) {
    this.options = { autoReconnect: true, ...options }
    this.myUserId = parseUserId(getToken())
  }

  // ---------- 对外 API ----------

  /** 建立连接（重复调用会先关掉旧连接） */
  connect() {
    this.closedByUser = false

    const token = getToken()
    if (!token) {
      this.emitStatus('offline')
      this.emit({ type: 'system', content: '请先登录后再联系客服' })
      return
    }

    this.emitStatus('connecting')
    // 浏览器无法自定义请求头，token 只能放在 URL 上（后端 AuthHandler 从这里取）
    const url = `${WS_BASE}${WS_PATH}?token=${encodeURIComponent(token)}`
    const ws = new WebSocket(url)
    this.ws = ws

    ws.onopen = () => {
      this.retryCount = 0
      this.emitStatus('online')
      this.startHeartbeat()
    }

    ws.onmessage = (ev) => {
      let data: ChatInbound
      try {
        data = JSON.parse(String(ev.data))
      } catch {
        // 服务端只发 JSON；真收到别的就当普通文本兜底，别让界面白屏
        this.emit({ type: 'system', content: String(ev.data) })
        return
      }
      // 心跳回执不用暴露给界面
      if (data.type !== 'pong') {
        this.emit(data)
      }
    }

    ws.onclose = () => {
      this.stopHeartbeat()
      this.emitStatus('offline')
      this.scheduleReconnect()
    }

    // onerror 一定会跟着 onclose，这里只记一下，提示交给 onclose 统一给
    ws.onerror = () => {
      /* noop */
    }
  }

  /**
   * 发送一条消息。
   *
   * @returns 是否发送成功（连接不可用时返回 false，调用方据此提示用户）
   */
  send(content: string): boolean {
    const text = content.trim()
    if (!text) return false
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false

    this.ws.send(
      JSON.stringify({
        type: 'chat',
        to: this.options.toUserId,
        content: text,
        // 客户端生成，配合后端唯一索引做幂等：网络重试也不会重复入库
        clientMsgId: newId(),
      }),
    )
    return true
  }

  /** 主动关闭：不会再自动重连 */
  close() {
    this.closedByUser = true
    this.stopHeartbeat()
    this.clearRetry()
    this.ws?.close()
    this.ws = null
    this.emitStatus('offline')
  }

  /** 手动重连（界面上的「重新连接」按钮） */
  reconnect() {
    this.clearRetry()
    this.retryCount = 0
    this.ws?.close()
    this.ws = null
    this.connect()
  }

  // ---------- 心跳与重连 ----------

  private startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        // 浏览器无法发协议级 ping 帧，只能发应用层心跳
        this.ws.send(JSON.stringify({ type: 'ping' }))
      }
    }, HEARTBEAT_MS)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer !== undefined) {
      window.clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = undefined
    }
  }

  private scheduleReconnect() {
    if (this.closedByUser || !this.options.autoReconnect) return
    if (this.retryCount >= MAX_RETRY) {
      this.emit({
        type: 'system',
        content: '连接失败：请确认 Netty 客服服务已在 9090 端口启动',
      })
      return
    }
    // 指数退避：3s、6s、12s… 最长 15s，避免服务端没起来时疯狂重试
    const delay = Math.min(3000 * 2 ** this.retryCount, MAX_RETRY_DELAY_MS)
    this.retryCount += 1
    this.retryTimer = window.setTimeout(() => this.connect(), delay)
  }

  private clearRetry() {
    if (this.retryTimer !== undefined) {
      window.clearTimeout(this.retryTimer)
      this.retryTimer = undefined
    }
  }

  // ---------- 事件派发 ----------

  private emit(msg: ChatInbound) {
    this.options.onMessage?.(msg)
  }

  private emitStatus(status: ChatStatus) {
    this.options.onStatusChange?.(status)
  }
}

// ==================== 工具 ====================

/** 生成客户端消息ID：优先用原生 randomUUID */
function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 从 JWT 的 payload.sub 里解出当前用户 id，解析失败返回 null */
function parseUserId(token: string | null | undefined): number | null {
  if (!token) return null
  try {
    const payload = JSON.parse(atob(token.split('.')[1])) as { sub?: string }
    const id = Number(payload.sub)
    return Number.isFinite(id) ? id : null
  } catch {
    return null
  }
}
