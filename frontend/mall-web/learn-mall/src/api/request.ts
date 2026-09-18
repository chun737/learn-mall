import axios from 'axios'
import { getToken, clearAuth } from '@/utils/auth'

// 定义后端返回格式的类型（interface = 描述数据长什么样的"契约"）
// 对应后端 com.mall.common.Result：所有接口都包着这层壳
interface Result<T> {
  code: number
  message: string
  data: T
}

// 创建 axios 实例：项目里所有请求都走它，超时、拦截器统一配置
const request = axios.create({
  timeout: 10000,
  // 统一加 /api 前缀，各接口文件里仍写后端原始路径（如 /product）：
  //   实际发出 → /api/product
  //   vite 代理命中 /api → rewrite 去掉前缀 → 后端收到 /product
  // 好处：① 与前端页面路由（/product/:id）彻底隔开，刷新详情页不再被代理截走；
  //       ② 以后新增接口自动带前缀，不会漏。
  baseURL: '/api',
})

/**
 * 未授权（401）回调：由 main.ts 注入"清登录态 + 跳登录页"的具体实现。
 *
 * 为什么不在这里直接 import router ？
 *   request.ts → router → 页面组件 → api/*.ts → request.ts 会形成循环依赖。
 *   改成"注册回调"后依赖方向变成单向（main.ts 同时认识 router 和 request），更安全。
 */
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler
}

// ===== 请求拦截器：给每个请求自动带上 token =====
// 与后端 JwtAuthenticationFilter 的约定：Authorization: Bearer <token>
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：每个响应在交给页面代码之前，先在这里"过一道关卡"
request.interceptors.response.use(
  (resp) => {
    // resp.data 才是响应体，即后端的 {code, message, data}
    const res = resp.data as Result<unknown>
    // 判断它确实是后端的 Result 壳（防御：万一哪天返回了别的格式，直接透传不拆）
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code !== 200) {
        // 业务失败：转成失败的 Promise，页面 catch 里接住
        return Promise.reject(new Error(res.message || `请求失败(code=${res.code})`))
      }
      // 业务成功：剥掉壳，页面拿到的直接是 data
      return res.data
    }
    return res
  },
  (err) => {
    const status = err?.response?.status

    // 401 = 未登录 / token 失效（后端 SecurityConfig 的 authenticationEntryPoint）
    // 处理：清掉本地登录态 → 通知外层跳登录页（带 redirect 回跳原页面）
    if (status === 401) {
      clearAuth()
      unauthorizedHandler?.()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }

    // 403 = 已登录但权限不足（accessDeniedHandler），例如普通用户访问 /admin/**
    // 只提示，绝不跳登录页 —— 否则已登录的用户会被莫名其妙绕回登录
    if (status === 403) {
      return Promise.reject(new Error('无权限访问'))
    }

    // 其余 HTTP 层面失败（网络断、404、500、超时）：统一翻译成一句中文错误
    const msg = err?.response?.data?.message || err.message || '网络异常'
    return Promise.reject(new Error(msg))
  },
)

export default request
