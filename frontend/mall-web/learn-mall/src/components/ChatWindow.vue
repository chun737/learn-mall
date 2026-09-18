<script setup lang="ts">
/**
 * 客服聊天窗口（QQ 风格）—— 对接项目里的 Netty 客服服务。
 *
 * 后端：src/main/java/com/mall/netty/ChatServer.java
 *   · 地址：ws://localhost:9090/ws/chat?token=<JWT>   （9090，不是 Spring 的 8080，也不走 Vite 代理）
 *   · 鉴权：AuthHandler 从 URL 取 token；无效/没带 → 直接断开
 *   · 收发都是统一 JSON 信封，按 type 分发（不再靠"首字符是不是 {" 猜）：
 *       发：{"type":"chat","to":9,"content":"文字","clientMsgId":"uuid"}
 *       发：{"type":"ping"}
 *       收：{"type":"chat","from":9,"content":"...","time":"..."}
 *       收：{"type":"history","list":[...]}   上线时补发的离线消息
 *       收：{"type":"system"|"error","content":"..."}
 *       收：{"type":"pong"}
 *   · 心跳：每 30 秒发一次 ping。服务端 90 秒收不到任何消息就断开连接
 *
 * 两种用法：
 *   <ChatWindow @close="..." />   弹窗形态（自带遮罩，点遮罩/× 关闭）
 *   <ChatWindow embedded />       内嵌形态（独立路由页用：无遮罩、不显示关闭按钮、撑满容器）
 */
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { getToken, isLoggedIn } from '@/utils/auth'

const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

/** 客服 userId：Netty 教程约定客服为 9（DEMO-9-admin）。正式环境应由后端下发 */
const SERVICE_USER_ID = 9

/** 聊天服务地址：Netty 独立进程（9090）。部署时用 VITE_WS_BASE 覆盖，别把 localhost 写死 */
const WS_BASE = (import.meta.env.VITE_WS_BASE as string | undefined) ?? 'ws://localhost:9090'
const WS_URL = `${WS_BASE}/ws/chat`

/** 心跳间隔：必须明显小于服务端的空闲判定时间（90 秒），否则会被判掉线 */
const HEARTBEAT_MS = 30_000

type Role = 'me' | 'other' | 'system'
interface ChatItem {
  id: number
  role: Role
  content: string
  time: string
}

const emit = defineEmits<{ (e: 'close'): void }>()

const messages = ref<ChatItem[]>([])
const draft = ref('')
const status = ref<'connecting' | 'online' | 'offline'>('connecting')

/** 消息列表容器：用来滚动到底部 */
const listEl = ref<HTMLElement>()

let ws: WebSocket | null = null
let seq = 0

/** 心跳定时器句柄 */
let heartbeat: number | undefined

/** 当前登录用户ID（取自 JWT 的 sub），用来判断收到的消息该放左边还是右边 */
const MY_ID = parseUserId(getToken())

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

/**
 * 应用层心跳：浏览器无法发 WebSocket 协议的 ping 帧，只能自己发业务消息。
 * 服务端 90 秒收不到任何消息会主动断开，靠它续命。
 */
function startHeartbeat() {
  stopHeartbeat()
  heartbeat = window.setInterval(() => {
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'ping' }))
    }
  }, HEARTBEAT_MS)
}

function stopHeartbeat() {
  if (heartbeat !== undefined) {
    window.clearInterval(heartbeat)
    heartbeat = undefined
  }
}

const statusText = computed(() =>
  status.value === 'online' ? '在线' : status.value === 'connecting' ? '连接中…' : '已断开',
)

function now(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 追加一条消息并滚到底部 */
function push(role: Role, content: string) {
  messages.value.push({ id: ++seq, role, content, time: now() })
  scrollToBottom()
}

/** 等 DOM 更新完再滚——否则新气泡还没渲染，scrollHeight 是旧值 */
async function scrollToBottom() {
  await nextTick()
  const el = listEl.value
  if (el) el.scrollTop = el.scrollHeight
}

function connect() {
  // 没登录连不上（后端会拒），先给明确提示
  if (!isLoggedIn()) {
    status.value = 'offline'
    push('system', '请先登录后再联系客服')
    return
  }

  status.value = 'connecting'

  // 标记"是否出错"：WebSocket 失败时 onerror 与 onclose 会**先后触发**。
  // 用它在 onclose 里区分"异常断开"和"正常关闭"，并保证一定会给出一条提示。
  // （旧写法在 onclose 里用 status==='offline' 做守卫，会把提示整个吞掉——这次修掉）
  let errored = false

  const url = `${WS_URL}?token=${encodeURIComponent(getToken())}`
  ws = new WebSocket(url)

  ws.onopen = () => {
    status.value = 'online'
    push('system', '已接入客服（电话 400-888-8888 · 9:00-21:00）')
    startHeartbeat()
  }

  // 服务端发来的都是统一 JSON 信封，按 type 分发（不再靠首字符猜）
  ws.onmessage = (ev) => {
    let data: {
      type?: string
      list?: Array<{ content?: string; from?: number }>
      content?: string
      from?: number
    }
    try {
      data = JSON.parse(String(ev.data))
    } catch {
      // 理论上服务端只发 JSON；真收到非 JSON 就按普通消息兜底
      push('other', String(ev.data))
      return
    }

    switch (data.type) {
      case 'chat': {
        // from === 自己 → 显示在右侧（上线补发的历史里会包含自己发过的消息）
        const mine = MY_ID !== null && data.from === MY_ID
        push(mine ? 'me' : 'other', data.content ?? '')
        break
      }
      case 'history':
        // 离线期间的消息，服务端已按时间正序排好
        for (const item of data.list ?? []) {
          const mine = MY_ID !== null && item.from === MY_ID
          push(mine ? 'me' : 'other', item.content ?? '')
        }
        break
      case 'system':
        push('system', data.content ?? '')
        break
      case 'error':
        push('system', `发送失败：${data.content ?? '未知错误'}`)
        break
      case 'pong':
        break // 心跳回执，不用显示
      default:
        break
    }
  }

  ws.onerror = () => {
    errored = true
  }

  ws.onclose = () => {
    stopHeartbeat()
    status.value = 'offline'
    push(
      'system',
      errored
        ? '连接失败：请确认 Netty 客服服务已在 9090 端口启动（日志里会写明是 token 无效还是端口没起来）'
        : '连接已断开',
    )
  }
}

/** 手动重连（失败时消息区会出现这个按钮） */
function reconnect() {
  ws?.close()
  ws = null
  push('system', '正在重新连接…')
  connect()
}

function send() {
  const text = draft.value.trim()
  if (!text) return

  if (!ws || ws.readyState !== WebSocket.OPEN) {
    push('system', '尚未连接到客服，请点「重新连接」后重试')
    return
  }

  // 按后端约定：{"type":"chat","to":客服id,"content":文字,"clientMsgId":UUID}
  // clientMsgId 让服务端能识别"同一条消息重复发送"，避免网络重试导致重复入库
  ws.send(
    JSON.stringify({
      type: 'chat',
      to: SERVICE_USER_ID,
      content: text,
      clientMsgId: newId(),
    }),
  )
  push('me', text)   // 先本地回显，不等服务端（服务端不会把自己的消息回传给自己）
  draft.value = ''
}

/** 生成客户端消息ID，用于服务端幂等去重 */
function newId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** Enter 发送；Shift+Enter 换行 */
function onEnter(e: KeyboardEvent) {
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function close() {
  emit('close')
}

/** 点遮罩关闭：内嵌模式没有遮罩，不响应 */
function onMaskClick() {
  if (!props.embedded) close()
}

connect()

// 组件卸载（关窗 / 离开页面）时断开连接，否则会一直挂在服务端的路由表里
onBeforeUnmount(() => {
  stopHeartbeat()
  ws?.close()
  ws = null
})
</script>

<template>
  <div :class="embedded ? 'chat-embed' : 'chat-mask'" @click.self="onMaskClick">
    <div class="chat" :class="{ 'chat-page': embedded }">
      <!-- ===== 头部：客服名 + 在线状态 ===== -->
      <div class="chat-head">
        <div class="head-left">
          <span class="avatar">客服</span>
          <div class="head-info">
            <div class="head-name">在线客服</div>
            <div class="head-status" :class="status">
              <i class="dot"></i>
              <span>{{ statusText }}</span>
            </div>
          </div>
        </div>
        <button v-if="!embedded" class="close-btn" @click="close">×</button>
      </div>

      <!-- ===== 消息区：可滚动 ===== -->
      <div ref="listEl" class="chat-body">
        <div v-for="m in messages" :key="m.id" class="row" :class="m.role">
          <!-- 系统消息：居中灰底小字 -->
          <div v-if="m.role === 'system'" class="system">{{ m.content }}</div>

          <!-- 对话气泡：对方靠左（白底），自己靠右（红底白字） -->
          <template v-else>
            <span v-if="m.role === 'other'" class="avatar-sm">客</span>
            <div class="bubble">
              <p class="bubble-text">{{ m.content }}</p>
              <span class="bubble-time">{{ m.time }}</span>
            </div>
            <span v-if="m.role === 'me'" class="avatar-sm me">我</span>
          </template>
        </div>

        <!-- 没有消息时：按连接状态给不同文案，失败时提供"重新连接" -->
        <div v-if="!messages.length" class="empty">
          <p class="empty-text">
            {{ status === 'offline' ? '未连接到客服' : '正在连接客服…' }}
          </p>
          <button v-if="status === 'offline'" class="retry-btn" @click="reconnect">
            重新连接
          </button>
        </div>
      </div>

      <!-- ===== 输入区 ===== -->
      <div class="chat-foot">
        <textarea
          v-model="draft"
          class="input"
          rows="2"
          placeholder="请输入消息，Enter 发送 / Shift+Enter 换行"
          @keydown.enter="onEnter"
        ></textarea>
        <button class="send-btn" :disabled="!draft.trim()" @click="send">发送</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 遮罩（弹窗模式） ===== */
.chat-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
/* ===== 内嵌模式（独立路由页）：无遮罩、撑满容器 ===== */
.chat-embed {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
}

.chat {
  width: 92%;
  max-width: 420px;
  height: 560px;              /* 固定高度：聊天窗必须有确定高度，消息区才能滚动 */
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;     /* 头 / 身体 / 脚 三段纵向排布 */
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.2);
}
/* 内嵌模式覆盖：铺满、更宽、去阴影（父容器提供边框） */
.chat.chat-page {
  width: 100%;
  max-width: 760px;
  height: 100%;
  border-radius: 12px;
  box-shadow: none;
  border: 1px solid #ececec;
}

/* ===== 头部 ===== */
.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #f7f7f7;
  border-bottom: 1px solid #ececec;
  flex-shrink: 0;
}
.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: linear-gradient(135deg, #ff6057, #e1251b);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.head-name {
  font-size: 14px;
  font-weight: 700;
  color: #333;
}
.head-status {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}
.head-status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #bbb;
}
.head-status.online .dot { background: #2ecc71; }
.head-status.connecting .dot { background: #f1c40f; }
.head-status.offline .dot { background: #bbb; }
.close-btn {
  border: none;
  background: none;
  font-size: 20px;
  line-height: 1;
  color: #999;
  cursor: pointer;
}
.close-btn:hover { color: #e1251b; }

/* ===== 消息区 ===== */
.chat-body {
  flex: 1;                    /* 吃掉剩余高度 */
  overflow-y: auto;           /* 内容超出就滚动 */
  padding: 14px;
  background: #f5f6f7;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.row.me {
  justify-content: flex-end;  /* 自己的消息靠右 */
}
.row.system {
  justify-content: center;
}
.system {
  font-size: 12px;
  color: #999;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 10px;
  padding: 4px 10px;
  text-align: center;
  max-width: 90%;
  line-height: 1.6;
}

/* 小头像 */
.avatar-sm {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  font-size: 12px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #c4c9cf;
}
.avatar-sm.me {
  background: #e1251b;
}

/* 气泡 */
.bubble {
  max-width: 74%;
  padding: 8px 12px 6px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #eaeaea;
}
.row.me .bubble {
  background: #e1251b;
  border-color: #e1251b;
}
.bubble-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
  white-space: pre-wrap;      /* 保留换行，长文本自动折行 */
  word-break: break-word;
}
.row.me .bubble-text {
  color: #fff;
}
.bubble-time {
  display: block;
  margin-top: 4px;
  font-size: 10px;
  color: #bbb;
  text-align: right;
}
.row.me .bubble-time {
  color: rgba(255, 255, 255, 0.7);
}

/* 空状态 + 重连按钮 */
.empty {
  margin: auto;
  text-align: center;
}
.empty-text {
  margin: 0 0 12px;
  font-size: 13px;
  color: #aaa;
}
.retry-btn {
  border: 1px solid #e1251b;
  background: #fff;
  color: #e1251b;
  border-radius: 6px;
  padding: 6px 18px;
  font-size: 13px;
  cursor: pointer;
}
.retry-btn:hover {
  background: #fff5f4;
}

/* ===== 输入区 ===== */
.chat-foot {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #ececec;
  background: #fff;
  flex-shrink: 0;
}
.input {
  flex: 1;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: inherit;
  resize: none;
  outline: none;
  line-height: 1.5;
}
.input:focus {
  border-color: #e1251b;
}
.send-btn {
  align-self: flex-end;
  width: 68px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: #e1251b;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.send-btn:hover {
  background: #c91f17;
}
.send-btn:disabled {
  background: #f0b7b3;
  cursor: not-allowed;
}
</style>
