import { createRouter, createWebHistory } from 'vue-router'

// 路由表：整个项目的"网址 → 页面"地图
const router = createRouter({
  history: createWebHistory(),      // History 模式：无 # 号的干净网址
  routes: [
    {
      path: '/',                    // 首页
      name: 'home',
      component: () => import('@/view/home/Home.vue'),
    },
    {
      path: '/login',               // 登录页：被鉴权拦截后跳到这里，登录成功再跳回原页面
      name: 'login',
      component: () => import('@/view/login/Login.vue'),
    },
    {
      path: '/search',              // 搜索结果页：关键词放 query（/search?keyword=手机）
      name: 'search',
      component: () => import('@/view/search/Search.vue'),
    },
    {
      path: '/checkout',            // 结算页：从购物车「去结算」进来（组件内自己校验登录）
      name: 'checkout',
      component: () => import('@/view/checkout/Checkout.vue'),
    },
    {
      path: '/orders',              // 我的订单：查看订单 + 去支付 + 取消/确认收货
      name: 'orders',
      component: () => import('@/view/orders/Orders.vue'),
    },
    {
      path: '/chat',                // 在线客服页：可直接访问/分享，不必先进某个商品
      name: 'chat',
      component: () => import('@/view/chat/Chat.vue'),
    },
    {
      path: '/product/:id',         // :id 动态参数 → /product/13、/product/42...
      name: 'product-detail',
      component: () => import('@/view/detail/Detail.vue'),
    },
  ],
})

export default router
