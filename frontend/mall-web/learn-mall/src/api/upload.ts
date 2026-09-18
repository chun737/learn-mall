// 图片上传
// 后端有两个入口，路径不同、权限也不同：
//   POST /user/upload/image   —— 登录即可（改头像、评价晒图）
//   POST /admin/upload/image  —— 需要 ADMIN（后台传商品图）
//
// 两者都是 multipart/form-data，字段名固定为 file，返回图片的完整 URL（存的是 OSS 地址）。

import request from './request'

/** 用户端上传：POST /user/upload/image → 返回图片完整 URL */
export function uploadUserImage(file: File) {
  return uploadImage('/user/upload/image', file)
}

/** 管理端上传：POST /admin/upload/image → 返回图片完整 URL */
export function uploadAdminImage(file: File) {
  return uploadImage('/admin/upload/image', file)
}

/**
 * 公共实现：把 File 装进 FormData 再发。
 *
 * 三个细节：
 *   1. 字段名必须是 file（后端 @RequestParam("file") 写死了）；
 *   2. **不要手动设置 Content-Type** —— 让浏览器自动带上 boundary，
 *      手写 multipart/form-data 会漏掉 boundary 导致后端解析失败；
 *   3. 上传比普通接口慢，超时单独放宽到 30s（axios 实例默认 10s）。
 */
function uploadImage(url: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<unknown, string>(url, form, {
    timeout: 30_000,
  })
}
