// 认证接口：登录、获取当前用户信息。
// 对应后端 com.mall.controller.user.AuthController / UserController

import request from './request'

/** 后端 LoginResponse：POST /auth/login 的返回结构 */
export interface LoginResponse {
  token: string
  userId: number
  username: string
}

/** 后端 UserProfileVO：GET /user/profile 的返回、PUT /user/profile 的返回 */
export interface UserProfile {
  id: number
  username: string
  nickname?: string
  avatar?: string
  phone?: string
  email?: string
  /** 性别：0=未知 1=男 2=女 */
  gender?: number
  /** 账号状态：0=禁用 1=正常 */
  status?: number
  lastLoginAt?: string
  createdAt?: string
}

/**
 * 登录：POST /auth/login
 * 请求体对应后端 LoginRequest{username, password}
 */
export function login(username: string, password: string) {
  return request.post<unknown, LoginResponse>('/auth/login', { username, password })
}

/**
 * 当前用户信息：GET /user/profile
 * 需要登录（SecurityConfig 里 anyRequest().authenticated()）。
 * 用途：进"我的"页时拉完整资料；也可作为"token 是否仍有效的权威校验"。
 */
export function getProfile() {
  return request.get<unknown, UserProfile>('/user/profile')
}

// ===== 修改个人信息 =====

/** 后端 UpdateProfileDTO：PUT /user/profile 的请求体（字段均可选，传了才更新） */
export interface UpdateProfileParams {
  nickname?: string
  avatar?: string
  phone?: string
  email?: string
  /** 性别：0=未知 1=男 2=女 */
  gender?: number
}

/**
 * 修改个人信息：PUT /user/profile
 * 需要登录；返回更新后的完整个人信息（UserProfileVO）。
 */
export function updateProfile(params: UpdateProfileParams) {
  return request.put<unknown, UserProfile>('/user/profile', params)
}

// ===== 上传（当前用于头像） =====

/**
 * 上传图片：POST /user/upload/image
 *
 * ⭐ 三个关键点：
 *   1. 必须走 multipart/form-data，且字段名固定为 file（后端 @RequestParam("file")）；
 *   2. **不要手动设置 Content-Type**！axios 检测到 FormData 会自动带上
 *      multipart/form-data 和 boundary；手写会丢掉 boundary，后端直接解析失败；
 *   3. 单独放宽 timeout：request.ts 默认 10s，弱网传 5MB 图片很容易超时。
 *
 * @param file 用户从本地选择的图片文件
 * @returns 图片在 OSS 上的完整可访问 URL
 */
export function uploadImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<unknown, string>('/user/upload/image', formData, {
    timeout: 30000,
  })
}

// ===== 注册 =====

/** 后端 RegisterRequest：POST /auth/register 的请求体 */
export interface RegisterParams {
  username: string
  password: string
  nickname?: string
  phone?: string
  email?: string
  /** 性别：0=未知 1=男 2=女，不传后端默认 0 */
  gender?: number
}

/** 后端 RegisterResponse：注册即登录，直接带 token 回来 */
export interface RegisterResponse {
  id: number
  username: string
  nickname: string
  token: string
}

/**
 * 注册：POST /auth/register
 * 后端把"注册"和"登录"合并了（注册成功即签发 token），
 * 所以前端拿到 token 后直接 setAuth 就算登录成功，不用再跳一次登录。
 */
export function register(params: RegisterParams) {
  return request.post<unknown, RegisterResponse>('/auth/register', params)
}

// ===== 忘记密码 =====

/** 后端 ForgetPasswordDTO：PUT /user/password/forget 的请求体 */
export interface ForgetPasswordParams {
  username: string
  /** 注册时填写的手机号，用于身份验证 */
  phone: string
  newPassword: string
}

/**
 * 忘记密码：PUT /user/password/forget（注意是 PUT，不是 POST）
 * 校验规则与后端一致：手机号 ^1[3-9]\d{9}$、新密码 6~32 位。
 * 该接口在 SecurityConfig 白名单里，无需登录即可调用。
 */
export function forgetPassword(params: ForgetPasswordParams) {
  return request.put<unknown, string>('/user/password/forget', params)
}
