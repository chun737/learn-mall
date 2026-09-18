// 注意引入顺序：Element 的默认样式在前，自己的全局样式在后 ——
// 这样 main.css 里覆盖的 --el-color-primary 才能生效（同优先级下后者胜）
import 'element-plus/dist/index.css'   // ① Element Plus 默认样式（主色是蓝色 #409eff）
import './assets/main.css'             // ② 项目全局样式（在里面把主色改成了主题红）

import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'      // 中文语言包（分页/日期等组件用）
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'                  // 领入路由表
import { setUnauthorizedHandler } from './api/request'
import { clearAuth } from './utils/auth'

const app = createApp(App)

// ===== Element Plus =====
// 全量引入：学习项目够用、写法最简；生产环境可改按需引入（unplugin-vue-components）减小体积
app.use(ElementPlus, { locale: zhCn })

// 把所有图标注册为全局组件，模板里即可直接用 <el-icon><Search /></el-icon>
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

// 注册"未授权"回调：任何接口返回 401 时，统一清登录态并跳登录页，
// 带上当前地址（redirect），登录成功后自动跳回原页面。
// 放在这里而不是写在 request.ts 内部，是为了避免 request ↔ router 的循环依赖。
setUnauthorizedHandler(() => {
  clearAuth()
  const current = router.currentRoute.value
  if (current.path === '/login') return        // 已在登录页，不重复跳
  router.push({ path: '/login', query: { redirect: current.fullPath } })
})

app.use(router).mount('#app') // .use(router)：注册后 <router-view/> 才工作
