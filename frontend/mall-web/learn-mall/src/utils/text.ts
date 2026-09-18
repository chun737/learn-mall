/**
 * 文本格式化小工具（购物车、结算页共用）
 */

/**
 * 规格文案。
 *
 * 后端 specs 字段有时是普通文本，有时是 JSON 字符串（如 {"颜色":"黑","容量":"256G"}），
 * 直接渲染会把后者显示成一坨 JSON。这里两种都兜住：
 *   是 JSON 对象 → 拼成「键：值」用空格分隔；否则原样返回。
 */
export function specsText(s?: string | null): string {
  if (!s) return ''
  try {
    const obj = JSON.parse(s)
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
      return Object.entries(obj)
        .map(([k, v]) => `${k}：${v}`)
        .join('  ')
    }
  } catch {
    // 不是 JSON，走下面的原样返回
  }
  return s
}
