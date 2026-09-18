import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 开发服务器配置：把 /api 开头的请求代理到后端 8080，解决跨域。
  // 为什么用 /api 而不是 /product：
  //   前端页面路由里有 /product/:id（商品详情页）。若代理也配 '/product'，
  //   在详情页刷新时浏览器会真实请求 /product/13，被代理转发给后端并返回 JSON，
  //   页面就变成一坨 JSON 而不是详情页（SPA 内部 router.push 不发请求，所以只有刷新才暴露）。
  //   加一层 /api 前缀，把"后端接口"和"前端页面路由"彻底隔开，互不干扰。
  // rewrite：转发前去掉 /api，后端收到的仍是 /product，Controller 无需改动。
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
