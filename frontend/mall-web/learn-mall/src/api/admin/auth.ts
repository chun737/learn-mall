// 管理端登录
// 对应后端 admin/AuthController（@RequestMapping("/admin")）
//
// 与用户端登录的区别：
//   · 路径不同：/admin/auth/login（用户端是 /auth/login）
//   · 后端会校验该账号是否具备 ADMIN 角色，否则拒绝
//   · 登录成功拿到的 token 同样放 Authorization: Bearer，后续所有 /admin/** 接口都靠它

import request from '../request'
import type { LoginRequest, LoginResponse } from '../types'

/** 管理员登录：POST /admin/auth/login */
export function adminLogin(data: LoginRequest) {
  return request.post<unknown, LoginResponse>('/admin/auth/login', data)
}
