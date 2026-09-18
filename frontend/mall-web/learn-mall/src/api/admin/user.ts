// 用户管理（后台）
// 对应后端 admin/AdminUserController（@RequestMapping("/admin/user")）
//
// 四个接口：分页查询 / 创建用户 / 加角色 / 减角色。
// 角色用「编码」表示（USER / ADMIN），增删都传 { userId, roleCode }。

import request from '../request'
import type { AdminUserVO, AdminUserDTO, UserRoleDTO, AdminUserQuery, PageResult } from './types'

/** 用户列表分页：GET /admin/user?pageNum=&pageSize=&keyword= */
export function listUsers(params: AdminUserQuery) {
  return request.get<unknown, PageResult<AdminUserVO>>('/admin/user', { params })
}

/** 管理员创建用户（可同时绑定角色）：POST /admin/user/register */
export function createUser(data: AdminUserDTO) {
  return request.post<unknown, AdminUserVO>('/admin/user/register', data)
}

/**
 * 给用户分配角色：POST /admin/user/role
 * ⚠️ 后端这里用的是 **POST + @RequestBody**，不是常见的 PUT，别写错。
 */
export function assignRole(data: UserRoleDTO) {
  return request.post<unknown, void>('/admin/user/role', data)
}

/**
 * 移除用户角色：DELETE /admin/user/role
 *
 * DELETE 带请求体必须写在 config.data 里（axios 的第二个参数 position），
 * 写成像普通 POST 那样传第二个参数会变成 query string，后端 @RequestBody 收不到。
 */
export function removeRole(data: UserRoleDTO) {
  return request.delete<unknown, void>('/admin/user/role', { data })
}
