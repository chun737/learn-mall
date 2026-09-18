<script setup lang="ts">
/**
 * 在线客服页（独立路由 /chat）
 * 复用 ChatWindow 的"内嵌模式"铺满页面，好处是：可以直接分享/收藏这个地址，
 * 不必先找到某个商品再点"询问客服"。
 */
import { useRouter } from 'vue-router'
import ChatWindow from '@/components/ChatWindow.vue'

const router = useRouter()
</script>

<template>
  <div class="chat-page-wrap">
    <div class="top-bar">
      <button class="back-btn" @click="router.back()">‹ 返回</button>
      <span class="page-title">在线客服</span>
    </div>

    <!-- 内嵌模式：无遮罩、撑满下方区域 -->
    <div class="chat-area">
      <ChatWindow embedded />
    </div>
  </div>
</template>

<style scoped>
.chat-page-wrap {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px 20px;
  height: 100vh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;     /* 顶栏固定，聊天区吃掉剩余高度 */
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  flex-shrink: 0;
}
.back-btn {
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
}
.back-btn:hover {
  border-color: #e1251b;
  color: #e1251b;
}
.page-title {
  font-size: 16px;
  font-weight: 700;
}
.chat-area {
  flex: 1;
  min-height: 0;   /* 关键：允许 flex 子项收缩，内部消息区才能出现滚动条 */
}
</style>
