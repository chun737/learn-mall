<script setup lang="ts">
/**
 * 商品详情页（京东版式）：
 * 左侧大图（随所选规格切换）+ 右侧信息区（标题/价格/规格选择/数量/双按钮）
 * + 底部图文详情。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductDetail, type ProductDetailVO } from '@/api/product'
import { addCart } from '@/api/cart'
import { isLoggedIn } from '@/utils/auth'
// 客服聊天窗（QQ 风格，内部自带遮罩，对接 Netty 聊天服务）
import ChatWindow from '@/components/ChatWindow.vue'

const route = useRoute()
const router = useRouter()

const detail = ref<ProductDetailVO | null>(null)
const loading = ref(false)
const errorMsg = ref('')

const selectedSku = ref(0)     // 当前选中的 SKU 下标（默认第一个）
const qty = ref(1)             // 购买数量
const toast = ref('')          // 轻提示文字（代替 alert 的美观做法）
let toastTimer: ReturnType<typeof setTimeout> | undefined

// 当前选中 SKU 的快捷访问
const currentSku = computed(() => detail.value?.skus?.[selectedSku.value])

// 展示图：优先用选中 SKU 自己的图（仿京东"换颜色换主图"），没有则用商品主图
const displayImage = computed(() => currentSku.value?.image || detail.value?.mainImage || '')

/** 把 specs 字符串（JSON）解析成可读标签，如 "标准版"；解析失败就原样显示 */
function specsLabel(specs: string): string {
  try {
    const obj = JSON.parse(specs)
    return Object.values(obj).join(' / ') || specs
  } catch {
    return specs
  }
}

/** 轻提示：2 秒后自动消失 */
function showToast(msg: string) {
  toast.value = msg
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2000)
}

/**
 * 登录守卫：需要登录的动作统一先过这里。
 * 未登录 → 跳登录页，并把当前地址塞进 redirect，登录成功后回到本页；
 * 返回 true 表示已登录、可以继续。
 */
function requireLogin(): boolean {
  if (isLoggedIn()) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

/** 加入购物车：先鉴权 → 再调接口（token 由 api/request.ts 的请求拦截器自动带上） */
async function addToCart() {
  if (!requireLogin()) return
  const sku = currentSku.value
  if (!sku) {
    showToast('请先选择规格')
    return
  }
  try {
    await addCart(Number(sku.id), qty.value)
    showToast(`已加入购物车 ×${qty.value}`)
  } catch (e) {
    // 失败可能是：token 失效（拦截器已统一跳登录）、库存不足、其他业务错误
    showToast(e instanceof Error ? e.message : '加入购物车失败')
  }
}

/**
 * 立即购买：不经过购物车，直接带着「商品 + 规格 + 数量」进结算页。
 *
 * 为什么把参数放 URL 而不是用全局状态：
 *   结算页刷新后不丢、能直接分享、后退也能回到这次购买意图，
 *   和搜索页把关键词放 query 是同一个取舍。
 */
function buyNow() {
  if (!requireLogin()) return
  const sku = currentSku.value
  if (!sku) {
    showToast('请先选择规格')
    return
  }
  if (sku.stock != null && qty.value > sku.stock) {
    showToast(`库存仅剩 ${sku.stock} 件`)
    return
  }
  router.push({
    path: '/checkout',
    query: {
      productId: String(route.params.id),
      skuId: String(sku.id),
      quantity: String(qty.value),
    },
  })
}

// ===== 右上角：询问客服 / 商品反馈 =====
type Panel = 'service' | 'feedback'

/** 当前打开的弹窗：null = 关闭 */
const panel = ref<Panel | null>(null)

/** 反馈类型选项（固定配置，不需要响应式） */
const FEEDBACK_TYPES = ['商品描述不符', '图片与实物差异', '质量问题', '价格问题', '其他']

const fbType = ref(FEEDBACK_TYPES[0])   // 选中的反馈类型
const fbContent = ref('')               // 反馈内容
const fbError = ref('')                 // 反馈表单的校验提示

function openPanel(p: Panel) {
  panel.value = p
  fbError.value = ''                    // 每次打开清掉上次的红字
}

/** 询问客服：聊天要带 token 才能连（Netty 靠 token 认人），所以先过登录守卫 */
function openService() {
  if (!requireLogin()) return
  panel.value = 'service'
}

function closePanel() {
  panel.value = null
}

/**
 * 提交商品反馈。
 * 后端目前没有反馈接口，所以先做“本地校验 + 提示”；
 * 将来接接口时只需把下面这行替换成 request.post(...)，
 * 并把 route.params.id 作为 productId 一起提交（后台需要知道是哪个商品）。
 */
function submitFeedback() {
  if (fbContent.value.trim().length < 5) {
    fbError.value = '请至少填写 5 个字，方便我们定位问题'
    return
  }
  const type = fbType.value
  console.info('[商品反馈]', {
    productId: route.params.id,
    type,
    content: fbContent.value.trim(),
  })

  // 关闭并重置表单
  panel.value = null
  fbContent.value = ''
  fbError.value = ''
  showToast(`已收到反馈（${type}），感谢你的建议`)
}

onMounted(async () => {
  const id = Number(route.params.id)          // 路由参数是字符串，必须转数字
  loading.value = true
  try {
    detail.value = await getProductDetail(id)
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="detail-page">
    <!-- 顶栏：返回 + 标题 -->
    <div class="top-bar">
      <button class="back-btn" @click="router.back()">‹ 返回</button>
      <span class="page-title">商品详情</span>

      <!-- 右上角：客服 / 反馈（margin-left:auto 把它们推到最右） -->
      <div class="top-actions">
        <button class="top-action" @click="openService">询问客服</button>
        <button class="top-action" @click="openPanel('feedback')">商品反馈</button>
      </div>
    </div>

    <!-- 三种状态：出错 / 加载中 / 正常 -->
    <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
    <p v-else-if="loading" class="tip">加载中...</p>
    <p v-else-if="!detail" class="tip">商品不存在或已下架</p>

    <div v-else class="detail-body">
      <!-- ===== 左：大图 + 规格选择（整栏占满左侧） ===== -->
      <div class="img-area">
        <img v-if="displayImage" :src="displayImage" :alt="detail.productName" />
        <div v-else class="ph">暂无图片</div>

        <!-- 规格选择：放在主图正下方，宽度撑满左侧整栏。
             点某个规格 → selectedSku 变 → computed 的 displayImage 重新计算
             → 上方主图自动切换成该 SKU 自己的 image（没有则回退到商品主图） -->
        <div v-if="detail.skus && detail.skus.length" class="sku-block">
          <div class="sku-title">选择规格</div>
          <div class="sku-list">
            <div
              v-for="(sku, index) in detail.skus"
              :key="sku.id"
              class="sku-item"
              :class="{ active: selectedSku === index }"
              @click="selectedSku = index"
            >
              {{ specsLabel(sku.specs) }}
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 右：信息区（京东版式从上到下） ===== -->
      <div class="info-area">
        <!-- 标题 -->
        <h2 class="name">{{ detail.productName }}</h2>
        <p class="sub">{{ detail.subTitle }}</p>

        <!-- 价格区：红底大价格（当前选中 SKU 的价格） -->
        <div class="price-area">
          <span class="price">
            <i>¥</i>{{ (Number(currentSku?.price ?? 0) * qty).toFixed(2) }}
          </span>
          <span class="price-note">到手价 · 已选 {{ qty }} 件</span>
        </div>

        <!-- 促销信息行 -->
        <div class="promo-row">
          <span class="promo-tag">促销</span>
          <span>已售 {{ currentSku?.sales ?? 0 }} 件 · 好评如潮</span>
        </div>

        <!-- 数量步进器：- 数字 + -->
        <div class="sku-title">数量</div>
        <div class="qty-row">
          <button class="qty-btn" :disabled="qty <= 1" @click="qty--">-</button>
          <span class="qty-num">{{ qty }}</span>
          <button class="qty-btn" @click="qty++">+</button>
        </div>

        <!-- 双按钮：京东式红块 -->
        <div class="btn-row">
          <button class="btn cart" @click="addToCart">加入购物车</button>
          <button class="btn buy" @click="buyNow">立即购买</button>
        </div>
      </div>
    </div>

    <!-- 客服聊天窗：QQ 风格，自带遮罩，内部对接 Netty 聊天服务 -->
    <ChatWindow v-if="panel === 'service'" @close="closePanel" />

    <!-- 商品反馈弹窗（点遮罩空白处或 × 关闭） -->
    <Transition name="dialog">
      <div v-if="panel === 'feedback'" class="dialog-mask" @click.self="closePanel">
        <div class="dialog">
          <div class="dialog-head">
            <span class="dialog-title">商品反馈</span>
            <button class="dialog-close" @click="closePanel">×</button>
          </div>

          <div class="dialog-body">
            <div class="fb-label">反馈类型</div>
            <div class="fb-types">
              <span
                v-for="t in FEEDBACK_TYPES"
                :key="t"
                class="fb-type"
                :class="{ active: fbType === t }"
                @click="fbType = t"
              >
                {{ t }}
              </span>
            </div>

            <div class="fb-label">问题描述</div>
            <textarea
              v-model="fbContent"
              class="fb-textarea"
              rows="4"
              placeholder="请描述你遇到的问题或建议（至少 5 个字）"
            ></textarea>
            <p v-if="fbError" class="fb-error">{{ fbError }}</p>

            <button class="dialog-btn" @click="submitFeedback">提交反馈</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 轻提示（代替 alert） -->
    <Transition name="toast">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </Transition>

    <!-- 图文详情 -->
    <div v-if="detail?.detail" class="detail-text">
      <h3 class="block-title">商品介绍</h3>
      <p class="detail-content">{{ detail.detail }}</p>
    </div>
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 0px 30px 30px;
  position: relative;          /* 轻提示 toast 的定位锚点 */
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
}
.back-btn {
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
}
.back-btn:hover { border-color: #e1251b; color: #e1251b; }
.page-title {
  font-size: 16px;
  font-weight: 700;
}
.error { color: #c0392b; text-align: center; padding: 30px 0; }
.tip { color: #999; text-align: center; padding: 30px 0; }

/* ===== 主体：左图右信息 ===== */
.detail-body {
  display: flex;
  gap: 20px;
  background: #fff;
  border-radius: 10px;
  padding: 16px;
}
.img-area {
  width: 320px;
  flex-shrink: 0;
}
.img-area img {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  border-radius: 8px;
  display: block;
  background: #fafafa;
}
.ph {
  width: 100%;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ccc;
  background: linear-gradient(135deg, #f6f6f6, #ededed);
  border-radius: 8px;
}
.info-area {
  flex: 1;
}

/* 标题区 */
.name {
  margin: 0 0 8px;
  font-size: 18px;
  line-height: 1.5;
}
.sub {
  margin: 0 0 14px;
  color: #e1251b;
  font-size: 13px;
}

/* 价格区：京东式红底横幅 */
.price-area {
  background: linear-gradient(90deg, #e1251b, #ff6057);
  color: #fff;
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.price {
  font-size: 26px;
  font-weight: 700;
}
.price i {
  font-style: normal;
  font-size: 15px;
}
.price-note {
  font-size: 12px;
  opacity: 0.9;
}

/* 促销行 */
.promo-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
  font-size: 13px;
  color: #666;
}
.promo-tag {
  background: #fdecea;
  color: #e1251b;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 3px;
}

/* 规格选择：挂在左侧主图下方，宽度撑满左侧整栏 */
.sku-block {
  margin-top: 14px;           /* 与上方主图拉开距离 */
}
.sku-title {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
}
.sku-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
/* flex: 1 1 auto + min-width —— 每个规格项自动伸展填满整行：
   1 个规格独占整栏；2 个各占一半；一行放不下自动换行继续铺满 */
.sku-item {
  flex: 1 1 auto;
  min-width: 72px;
  box-sizing: border-box;
  text-align: center;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 8px 10px;
  cursor: pointer;
  font-size: 13px;
  color: #333;
  background: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sku-item:hover { border-color: #e1251b; }
.sku-item.active {
  border-color: #e1251b;
  color: #e1251b;
  background: #fff5f4;
}

/* 数量步进器 */
.qty-row {
  display: flex;
  align-items: center;
  gap: 0;
}
.qty-btn {
  width: 32px;
  height: 32px;
  border: 1px solid #ddd;
  background: #fafafa;
  cursor: pointer;
  font-size: 16px;
}
.qty-btn:disabled { color: #ccc; cursor: not-allowed; }
.qty-num {
  width: 48px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid #ddd;
  border-bottom: 1px solid #ddd;
  font-size: 14px;
}

/* 双按钮：京东式红块 */
.btn-row {
  display: flex;
  gap: 12px;
  margin-top: 18px;
}
.btn {
  flex: 1;
  padding: 12px 0;
  border: none;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  color: #fff;
}
.btn.cart { background: #ff6057; }
.btn.cart:hover { background: #ff4d43; }
.btn.buy { background: #e1251b; }
.btn.buy:hover { background: #c91f17; }

/* 轻提示 toast：底部浮出，2 秒消失 */
.toast {
  position: fixed;
  left: 50%;
  bottom: 60px;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: 13px;
  padding: 10px 20px;
  border-radius: 20px;
  z-index: 99;
}
.toast-enter-active, .toast-leave-active { transition: opacity 0.3s, transform 0.3s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(10px); }

/* ===== 顶栏右上角：客服 / 反馈入口 ===== */
.top-actions {
  margin-left: auto;           /* 把这一组推到顶栏最右端 */
  display: flex;
  gap: 8px;
}
.top-action {
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 13px;
  color: #666;
  cursor: pointer;
}
.top-action:hover { border-color: #e1251b; color: #e1251b; }

/* ===== 客服 / 反馈 弹窗 ===== */
.dialog-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;                /* 高于 toast 的 99，保证弹窗盖在最上层 */
}
.dialog {
  width: 92%;
  max-width: 380px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}
.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.dialog-title { font-size: 15px; font-weight: 700; }
.dialog-close {
  border: none;
  background: none;
  font-size: 20px;
  line-height: 1;
  color: #999;
  cursor: pointer;
}
.dialog-close:hover { color: #e1251b; }
.dialog-body { padding: 16px; }

/* 反馈面板 */
.fb-label { font-size: 13px; color: #999; margin-bottom: 8px; }
.fb-types { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.fb-type {
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12px;
  color: #333;
  cursor: pointer;
}
.fb-type:hover { border-color: #e1251b; }
.fb-type.active { border-color: #e1251b; color: #e1251b; background: #fff5f4; }
.fb-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 10px;
  font-size: 13px;
  font-family: inherit;        /* textarea 默认等宽字体，改成继承页面字体更协调 */
  resize: vertical;
  outline: none;
}
.fb-textarea:focus { border-color: #e1251b; }
.fb-error { margin: 8px 0 0; font-size: 12px; color: #c0392b; }

.dialog-btn {
  width: 100%;
  height: 40px;
  margin-top: 14px;
  color: #fff;
  background: #e1251b;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}
.dialog-btn:hover { background: #c91f17; }

/* 弹窗淡入淡出 */
.dialog-enter-active, .dialog-leave-active { transition: opacity 0.2s; }
.dialog-enter-from, .dialog-leave-to { opacity: 0; }

/* 图文详情 */
.detail-text {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  margin-top: 16px;
}
.block-title {
  margin: 0 0 8px;
  font-size: 15px;
  border-left: 3px solid #e1251b;
  padding-left: 8px;
}
.detail-content {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: #555;
  white-space: pre-wrap;
}
</style>
