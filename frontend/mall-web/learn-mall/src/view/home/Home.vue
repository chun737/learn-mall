<script setup lang="ts">
/**
 * 首页：搜索框（含联想浮层）+ 跑马灯 + 猜你喜欢。
 * 内容从 App.vue 搬入（接入路由后 App.vue 只剩 <router-view/> 壳）。
 */
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSuggest } from '@/api/product'
import type { UserProfile } from '@/api/auth'
import { clearAuth, getCurrentUser, getToken, setAuth, type CurrentUser } from '@/utils/auth'
// Element Plus 图标（组件已在 main.ts 全局注册，但作为 prop 传递时仍需 import 组件对象）
import {
  Search,
  User,
  UserFilled,
  Ticket,
  Location,
  ChatDotRound,
  ShoppingCart,
  List,
  SwitchButton,
} from '@element-plus/icons-vue'
// 跑马灯推荐位组件：搜索框下方的流动商品图
import GoodsMarquee from '@/components/GoodsMarquee.vue'
// 猜你喜欢：1 行 5 个的随机推荐区
import GuessYouLike from '@/components/GuessYouLike.vue'
// 修改个人信息弹窗
import ProfileDialog from '@/components/ProfileDialog.vue'
// 今日热品秒杀 · 垂直流动框（首页左侧）
import SeckillMarquee from '@/components/SeckillMarquee.vue'
// 收货地址弹窗
import AddressDialog from '@/components/AddressDialog.vue'
// 购物车弹窗
import CartDialog from '@/components/CartDialog.vue'

// ========== 第二部分：状态清单（页面要记住的东西） ==========
const keyword = ref('')                    // 搜索框内容，v-model 会双向绑定到输入框

// ===== 联想词：改用 el-autocomplete =====
// 它内置了防抖（见模板的 debounce 属性）和下拉浮层，
// 不用再手写 setTimeout + position:absolute + 点外部关闭那一套。
type SuggestItem = { value: string }

/**
 * el-autocomplete 要求"回调式"取数：拿到结果后调 cb(items) 通知它。
 * 这里把我们 Promise 风格的 getSuggest 包装成它要的形状。
 */
async function fetchSuggestions(query: string, cb: (items: SuggestItem[]) => void) {
  const kw = query.trim()
  if (!kw) {
    cb([])
    return
  }
  try {
    const words = await getSuggest(kw)
    cb((words ?? []).map((w) => ({ value: w })))
  } catch {
    cb([])                                 // 联想失败静默，不影响搜索主功能
  }
}

/** 选中某个联想词：回填并立即搜索 */
function onSuggestSelect(item: SuggestItem) {
  keyword.value = item.value
  doSearch()
}
// ========== 第三部分：搜索 = 跳到独立的结果页 ==========
/**
 * 点搜索按钮 / 按回车 / 选中联想词都走这里。
 *
 * 为什么不在首页原地展示结果，而是跳到一个独立页面：
 *   ① 结果页有自己的 URL（/search?keyword=xxx）→ 能刷新、能分享、能后退
 *   ② 首页只负责"导航 + 推荐"，不再背一套分页 / 排序 / 触底加载的状态
 */
function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) return                       // 卫语句：空关键词不跳转
  router.push({ path: '/search', query: { keyword: kw } })
}

// ========== "我的"入口（搜索框右侧） ==========
const route = useRoute()
const router = useRouter()

/**
 * 当前登录用户：null 表示未登录。
 * 用 ref 存一份而不是每次都调 getCurrentUser()，因为 localStorage 不是响应式的——
 * 退出登录时手动改这个 ref，视图才会跟着变。
 */
const currentUser = ref(getCurrentUser())
const menuOpen = ref(false)
const profileOpen = ref(false)     // 修改个人信息弹窗开关
const addressOpen = ref(false)     // 收货地址弹窗开关
const cartOpen = ref(false)        // 购物车弹窗开关

// ========== 轻提示（"我的"菜单里未完成的入口、退出登录用） ==========
const tip = ref('')
let tipTimer: ReturnType<typeof setTimeout> | undefined

function showTip(msg: string, ms = 1800) {
  tip.value = msg
  clearTimeout(tipTimer)
  tipTimer = setTimeout(() => (tip.value = ''), ms)
}

/** 未登录时点胶囊：去登录页（带 redirect，登录后回到首页） */
function goLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

/** 菜单里还没做的入口：先给个提示 */
function notReady(name: string) {
  menuOpen.value = false
  showTip(`${name}功能开发中，敬请期待`)
}

/** 在线客服：跳独立聊天页 */
function goChat() {
  menuOpen.value = false
  router.push('/chat')
}

/** 打开「修改信息」弹窗（先收起菜单，避免两层浮层叠着） */
function openProfile() {
  menuOpen.value = false
  profileOpen.value = true
}

/** 个人信息保存成功：把最新资料同步到本地登录态，顶部胶囊立即更新 */
function onProfileSaved(profile: UserProfile) {
  const next: CurrentUser = {
    userId: profile.id,
    username: profile.username,
    nickname: profile.nickname,
    avatar: profile.avatar,
  }
  currentUser.value = next
  setAuth(getToken(), next)     // 同步写回 localStorage，刷新页面也不丢
  showTip('个人信息已更新')
}

/** 退出登录：清掉本地 token 与用户信息，并收起菜单 */
function logout() {
  clearAuth()
  currentUser.value = null
  menuOpen.value = false
  showTip('已退出登录')
}

/** el-dropdown 的 command 回调：按命令分发（展开/收起、点外部关闭都由 Element 接管） */
function onMineCommand(cmd: string) {
  switch (cmd) {
    case 'profile':
      openProfile()
      break
    case 'chat':
      goChat()
      break
    case 'logout':
      logout()
      break
    case 'coupons':
      notReady('我的优惠券')
      break
    case 'addresses':
      addressOpen.value = true
      break
    case 'cart':
      cartOpen.value = true
      break
    case 'orders':
      menuOpen.value = false
      router.push('/orders')
      break
  }
}

// 组件卸载前清理轻提示定时器（开了什么就关什么）
onBeforeUnmount(() => {
  clearTimeout(tipTimer)
})
</script>

<template>
  <div class="page">
    <!-- 轻提示：退出登录 / 未完成入口的反馈，1.8 秒后自动消失 -->
    <Transition name="tip">
      <div v-if="tip" class="tip-bubble">{{ tip }}</div>
    </Transition>

    <!-- ===== 顶部区域：左侧「今日热品秒杀」+ 右侧「搜索 + 跑马灯」 ===== -->
    <div class="top-row">
      <!-- 左：今日热品秒杀（垂直流动框） -->
      <SeckillMarquee />

      <!-- 右：搜索栏 + 我的 + 跑马灯 -->
      <div class="top-right">
        <!-- ===== 搜索栏 + 我的（两个独立容器，横向排列） ===== -->
        <div class="search-area">
          <!-- 搜索区：Element 的 el-autocomplete（自带联想下拉 + 防抖）+ el-button -->
          <div class="search-bar">
            <el-autocomplete
              v-model="keyword"
              class="search-input"
              size="large"
              placeholder="输入商品关键词"
              :fetch-suggestions="fetchSuggestions"
              :trigger-on-focus="false"
              :debounce="200"
              :fit-input-width="true"
              clearable
              @select="onSuggestSelect"
              @keyup.enter="doSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-autocomplete>

            <el-button
              class="search-btn"
              type="primary"
              size="large"
              @click="doSearch"
            >
              搜索
            </el-button>
          </div>

          <!-- 我的：未登录=点击去登录；已登录=Element 下拉菜单 -->
          <div class="mine">
            <!-- 未登录 -->
            <button v-if="!currentUser" class="mine-btn" @click="goLogin">
              <el-avatar :size="28" :icon="UserFilled" class="mine-avatar guest" />
              <span class="mine-name">登录 / 注册</span>
              <span class="mine-caret">▾</span>
            </button>

            <!-- 已登录：trigger=click 点击展开；点外部自动关闭由 Element 处理 -->
            <el-dropdown
              v-else
              trigger="click"
              @command="onMineCommand"
              @visible-change="(v: boolean) => (menuOpen = v)"
            >
              <button class="mine-btn" :class="{ open: menuOpen }">
                <el-avatar :size="28" :src="currentUser.avatar" class="mine-avatar">
                  {{ currentUser.username.charAt(0).toUpperCase() }}
                </el-avatar>
                <span class="mine-name">{{ currentUser.nickname || currentUser.username }}</span>
                <span class="mine-caret">▾</span>
              </button>

              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile" :icon="User">修改信息</el-dropdown-item>
                  <el-dropdown-item command="orders" :icon="List">我的订单</el-dropdown-item>
                  <el-dropdown-item command="cart" :icon="ShoppingCart">购物车</el-dropdown-item>
                  <el-dropdown-item command="coupons" :icon="Ticket">我的优惠券</el-dropdown-item>
                  <el-dropdown-item command="addresses" :icon="Location">收货地址</el-dropdown-item>
                  <el-dropdown-item command="chat" :icon="ChatDotRound">在线客服</el-dropdown-item>
                  <el-dropdown-item command="logout" :icon="SwitchButton" divided>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <!-- ===== 跑马灯推荐位（横向流动）：常驻展示 ===== -->
        <GoodsMarquee />

        <!-- ===== 物品推荐（为你推荐）：放在推荐流动框正下方，同在右列内 ===== -->
        <GuessYouLike />
      </div>
    </div>

    <!-- 修改个人信息弹窗 -->
    <ProfileDialog v-if="profileOpen" @close="profileOpen = false" @saved="onProfileSaved" />

    <!-- 收货地址弹窗 -->
    <AddressDialog v-if="addressOpen" @close="addressOpen = false" />

    <!-- 购物车弹窗 -->
    <CartDialog v-if="cartOpen" @close="cartOpen = false" />
  </div>
</template>

<style scoped>
.page {
  /* 不再限宽/居中：让内容占满浏览器可用宽度（宽屏时右侧不留白） */
  padding: 0px 30px;
}
/* 顶部区域：左「秒杀流动框」+ 右「搜索 + 跑马灯」 */
.top-row {
  display: flex;
  gap: 16px;
  align-items: stretch;    /* 左右两列等高 → 秒杀框自动撑到和右侧一样高 */
  margin-bottom: 12px;
}
/* 右侧列：搜索区在上、跑马灯在下 */
.top-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
/* 搜索栏 + 我的，两个独立容器横向排列 */
.search-area {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 10px;        /* 撑满右列宽度（不再限宽） */
}
.search-bar {
  flex: 1;                 /* 占满"我的"之外的全部空间 */
  min-width: 0;            /* 允许收缩，避免长内容把容器撑破 */
  display: flex;
  gap: 8px;
}
/* el-autocomplete 撑满剩余宽度（class 挂在组件根元素上，scoped 能直接命中） */
.search-input {
  flex: 1;
  min-width: 0;
}
.error {
  color: #c0392b;
}
.tip {
  color: #999;
  text-align: center;
}

/* ===== 搜索按钮：颜色/圆角/禁用态都交给 Element 的 type="primary"，这里只调尺寸 ===== */
.search-btn {
  flex-shrink: 0;
  padding: 0 26px;
  font-weight: 700;
}

/* ===== "我的"入口：胶囊形用户中心 ===== */
.mine {
  flex: 0 0 260px;         /* 固定 260px 宽 */
}
/* 胶囊按钮：撑满容器，内部横向排布「头像 + 名字 + 箭头」 */
.mine-btn {
  width: 100%;
  height: 40px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px 0 6px;
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 999px;    /* 胶囊形：圆角取一个足够大的值即可 */
  font-size: 14px;
  color: #333;
  cursor: pointer;
}
.mine-btn:hover,
.mine-btn.open {
  border-color: #e1251b;
}
/* 头像：尺寸由 el-avatar 的 :size 控制，这里只覆盖配色
   （有图时 el-avatar 会自动渲染 <img>，不用再写图片样式） */
.mine-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #ff6057, #e1251b);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}
.mine-avatar.guest {
  background: #f0f0f0;
  color: #999;
}
/* 用户名：占满中间空间，过长省略号 */
.mine-name {
  flex: 1;
  text-align: left;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 下拉箭头：菜单展开时翻转 180° */
.mine-caret {
  flex-shrink: 0;
  font-size: 10px;
  color: #bbb;
  transition: transform 0.2s;
}
.mine-btn.open .mine-caret {
  transform: rotate(180deg);
}
/* 下拉菜单的样式已由 Element 的 el-dropdown-menu 提供，这里不再需要自写 */

/* ===== 轻提示气泡（顶部居中，自动淡出） ===== */
.tip-bubble {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: 14px;
  padding: 10px 22px;
  border-radius: 20px;
  z-index: 99;
}
.tip-enter-active,
.tip-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}
.tip-enter-from,
.tip-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-12px);
}
</style>
