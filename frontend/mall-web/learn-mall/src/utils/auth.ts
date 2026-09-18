/**
 * 登录态工具：token 与当前用户信息的本地存取。
 *
 * 设计原则（配合后端 JWT + Redis 白名单）：
 * - 本地只做"乐观判断"（有 token 就当作已登录），用于决定要不要弹登录框；
 * - token 真伪、是否被强制下线，一律由后端判断，前端靠 401 兜底。
 *
 * 为什么不用 Pinia？当前只有这一处状态，用 localStorage 最直接；
 * 以后要跨组件响应式共享（如顶部导航实时显示用户名）时，再迁到 store。
 */

const TOKEN_KEY = 'mall_token'
const USER_KEY = 'mall_user'

/** 当前登录用户：来自登录接口返回，仅用于展示，不能用于鉴权 */
export interface CurrentUser {
  userId: number
  username: string
  /** 昵称：修改个人信息后会有，展示时优先用它 */
  nickname?: string
  /** 头像 URL：来自 GET /user/profile */
  avatar?: string
}

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setAuth(token: string, user: CurrentUser): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 读取本地用户信息；数据被改坏时返回 null（当作未登录处理） */
export function getCurrentUser(): CurrentUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as CurrentUser
  } catch {
    return null
  }
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/**
 * 是否"看起来"已登录：只检查本地有没有 token。
 * 优点：0 请求、极快，适合"点击时决定要不要跳登录"。
 * 局限：token 可能已过期或被强制下线 —— 真正的判定由后端 401 完成
 *      （见 api/request.ts 的响应拦截器）。
 */
export function isLoggedIn(): boolean {
  return !!getToken()
}
