<script setup lang="ts">
/**
 * 我的订单 /orders
 *
 * 数据：GET /order?pageNum=&pageSize=&orderStatus=（不传 orderStatus = 查全部）
 * 操作：取消订单 / 确认收货 / 删除订单 / 去支付
 *
 * 状态与操作按钮的对应关系（后端 Constants.ORDER_STATUS_*）：
 *   0 待支付 → 可「取消订单」「去支付」
 *   1 已支付（待发货）→ 等发货，无操作
 *   2 已发货（待收货）→ 可「确认收货」
 *   3 已完成 / 4 已取消 / 5 已退款 → 可「删除订单」
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  cancelOrder,
  confirmOrder,
  deleteOrder,
  getOrders,
  ORDER_STATUS,
  type OrderListItem,
} from '@/api/order'
import { isLoggedIn } from '@/utils/auth'
import PayDialog from '@/components/PayDialog.vue'

const router = useRouter()

/** 状态筛选：value = null 表示查全部 */
const TABS = [
  { label: '全部', value: null },
  { label: '待支付', value: ORDER_STATUS.UNPAID },
  { label: '待发货', value: ORDER_STATUS.PAID },
  { label: '待收货', value: ORDER_STATUS.SHIPPED },
  { label: '已完成', value: ORDER_STATUS.COMPLETED },
  { label: '已取消', value: ORDER_STATUS.CANCELLED },
] as const

const tab = ref<number | null>(null)

const PAGE_SIZE = 10
const list = ref<OrderListItem[]>([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
const errorMsg = ref('')

/** 非 null 时打开收银台 */
const paying = ref<OrderListItem | null>(null)

const sentinel = ref<HTMLElement>()
let observer: IntersectionObserver | undefined

/** 清空结果，回到第一页（切 tab 或操作后刷新用） */
function reset() {
  list.value = []
  pageNum.value = 1
  finished.value = false
  errorMsg.value = ''
}

/** 追加一页 */
async function loadPage() {
  if (loading.value || finished.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const page = await getOrders(pageNum.value, PAGE_SIZE, tab.value ?? undefined)
    const rows = page.list ?? []
    list.value = [...list.value, ...rows]
    pageNum.value += 1
    finished.value = rows.length < PAGE_SIZE
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '订单加载失败'
  } finally {
    loading.value = false
    maybeLoadMore()
  }
}

/** 复查哨兵是否仍在预加载带内（防止 observer 不再触发导致卡住） */
function maybeLoadMore() {
  const el = sentinel.value
  if (!el) return
  if (el.getBoundingClientRect().top < window.innerHeight + 300) {
    loadPage()
  }
}

/** 操作后刷新：回到第一页重新拉，保证状态文本由后端给出 */
async function refresh() {
  reset()
  await loadPage()
}

function switchTab(v: number | null) {
  if (tab.value === v) return
  tab.value = v
  refresh()
}

// ===== 按钮可见性 =====
const canPay = (o: OrderListItem) => o.orderStatus === ORDER_STATUS.UNPAID
const canCancel = (o: OrderListItem) => o.orderStatus === ORDER_STATUS.UNPAID
const canConfirm = (o: OrderListItem) => o.orderStatus === ORDER_STATUS.SHIPPED
const canDelete = (o: OrderListItem) =>
  o.orderStatus === ORDER_STATUS.COMPLETED ||
  o.orderStatus === ORDER_STATUS.CANCELLED ||
  o.orderStatus === ORDER_STATUS.REFUNDED

// ===== 操作 =====
async function doCancel(o: OrderListItem) {
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${o.orderNo} 吗？取消后占用的库存会被释放。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '取消订单', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  try {
    await cancelOrder(o.orderNo)
    ElMessage.success('订单已取消')
    await refresh()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '取消失败')
  }
}

async function doConfirm(o: OrderListItem) {
  try {
    await ElMessageBox.confirm('确认已经收到货了吗？', '确认收货', {
      type: 'warning',
      confirmButtonText: '确认收货',
      cancelButtonText: '还没收到',
    })
  } catch {
    return
  }
  try {
    await confirmOrder(o.orderNo)
    ElMessage.success('确认收货成功')
    await refresh()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function doDelete(o: OrderListItem) {
  try {
    await ElMessageBox.confirm(`确定删除订单 ${o.orderNo} 吗？`, '删除订单', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteOrder(o.orderNo)
    ElMessage.success('订单已删除')
    await refresh()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function money(v: unknown) {
  return Number(v || 0).toFixed(2)
}

/** 后端给的是 ISO 格式（2026-09-15T20:10:30），截成「2026-09-15 20:10」 */
function fmtTime(s?: string) {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 16)
}

/** 状态配色：待支付=红，进行中=蓝，完成=绿，已取消/退款=灰 */
function statusClass(st: number) {
  if (st === ORDER_STATUS.UNPAID) return 'unpaid'
  if (st === ORDER_STATUS.CANCELLED || st === ORDER_STATUS.REFUNDED) return 'dead'
  if (st === ORDER_STATUS.COMPLETED) return 'done'
  return 'active'
}

onMounted(() => {
  if (!isLoggedIn()) {
    router.replace({ path: '/login', query: { redirect: '/orders' } })
    return
  }
  loadPage()

  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) maybeLoadMore()
    },
    { rootMargin: '300px' },
  )
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
})
</script>

<template>
  <div class="page">
    <header class="head">
      <button class="back" @click="router.push('/')">← 首页</button>
      <h1 class="title">我的订单</h1>
    </header>

    <!-- ===== 状态筛选 ===== -->
    <nav class="tabs">
      <span
        v-for="t in TABS"
        :key="String(t.value)"
        class="tab"
        :class="{ on: tab === t.value }"
        @click="switchTab(t.value)"
      >
        {{ t.label }}
      </span>
    </nav>

    <!-- ===== 订单列表 ===== -->
    <p v-if="!list.length && loading" class="hint">加载中…</p>
    <p v-else-if="!list.length && errorMsg" class="hint err">{{ errorMsg }}</p>
    <p v-else-if="!list.length" class="hint">这里还没有订单</p>

    <section v-else class="orders">
      <article v-for="o in list" :key="o.orderNo" class="order">
        <div class="o-head">
          <span class="o-no">{{ o.orderNo }}</span>
          <span class="o-time">{{ fmtTime(o.createdAt) }}</span>
          <span class="o-status" :class="statusClass(o.orderStatus)">
            {{ o.orderStatusText }}
          </span>
        </div>

        <div class="o-body">
          <div class="thumb">
            <img v-if="o.productImage" :src="o.productImage" :alt="o.productName" />
            <span v-else class="thumb-ph">无图</span>
          </div>
          <div class="o-info">
            <p class="o-name">{{ o.productName }}</p>
            <p class="o-count">共 {{ o.totalQuantity }} 件商品</p>
          </div>
          <span class="o-amount">应付 ¥{{ money(o.payAmount) }}</span>
        </div>

        <div class="o-foot">
          <button v-if="canCancel(o)" class="btn ghost" @click="doCancel(o)">
            取消订单
          </button>
          <button v-if="canDelete(o)" class="btn ghost" @click="doDelete(o)">
            删除订单
          </button>
          <button v-if="canConfirm(o)" class="btn" @click="doConfirm(o)">
            确认收货
          </button>
          <button v-if="canPay(o)" class="btn main" @click="paying = o">
            去支付
          </button>
        </div>
      </article>
    </section>

    <!-- ===== 触底哨兵 ===== -->
    <div ref="sentinel" class="sentinel">
      <span v-if="finished && list.length">— 已经到底啦 —</span>
      <span v-else-if="loading">加载中...</span>
      <span v-else-if="list.length">继续下滑查看更多 ↓</span>
    </div>

    <!-- ===== 收银台：点「确认支付」跳转到模拟收银台，付完跳回本页 ===== -->
    <PayDialog
      v-if="paying"
      :order-no="paying.orderNo"
      :amount="paying.payAmount"
      return-url="/orders"
      @close="paying = null"
    />
  </div>
</template>

<style scoped>
.page {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 16px 24px;
  min-height: 100vh;
  box-sizing: border-box;
}

.head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 0 12px;
}
.back {
  padding: 8px 14px;
  font-size: 13px;
  color: #333;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 8px;
  cursor: pointer;
}
.back:hover {
  border-color: #e1251b;
  color: #e1251b;
}
.title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

/* ===== 状态筛选 ===== */
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.tab {
  padding: 6px 16px;
  font-size: 13px;
  color: #333;
  background: #fff;
  border: 1px solid transparent;
  border-radius: 16px;
  cursor: pointer;
  user-select: none;
}
.tab:hover {
  color: #e1251b;
}
.tab.on {
  color: #e1251b;
  background: #fff5f4;
  border-color: #e1251b;
  font-weight: 700;
}

.hint {
  margin: 0;
  padding: 70px 0;
  text-align: center;
  font-size: 14px;
  color: #999;
}
.hint.err {
  color: #c0392b;
}

/* ===== 订单卡片 ===== */
.orders {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.order {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
}
.o-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fafafa;
  font-size: 12px;
  color: #999;
}
.o-no {
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.o-time {
  margin-left: auto;
  flex-shrink: 0;
}
.o-status {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 700;
}
.o-status.unpaid {
  color: #e1251b;
  background: #fdecea;
}
.o-status.active {
  color: #1677ff;
  background: #e9f2ff;
}
.o-status.done {
  color: #38a169;
  background: #e8f6ee;
}
.o-status.dead {
  color: #999;
  background: #f0f0f0;
}

.o-body {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
}
.thumb {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f5f5;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: #bbb;
}
.o-info {
  flex: 1;
  min-width: 0;
}
.o-name {
  margin: 0;
  font-size: 14px;
  color: #333;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.o-count {
  margin: 6px 0 0;
  font-size: 12px;
  color: #999;
}
.o-amount {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 700;
  color: #e1251b;
}

.o-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 14px 14px;
}
.btn {
  padding: 7px 16px;
  font-size: 13px;
  border-radius: 16px;
  cursor: pointer;
  border: 1px solid #ddd;
  background: #fff;
  color: #333;
}
.btn:hover {
  border-color: #e1251b;
  color: #e1251b;
}
.btn.ghost {
  color: #888;
}
.btn.main {
  background: #e1251b;
  border-color: #e1251b;
  color: #fff;
  font-weight: 700;
}
.btn.main:hover {
  background: #c91f17;
  color: #fff;
}

.sentinel {
  text-align: center;
  color: #999;
  font-size: 12px;
  padding: 16px 0 4px;
}
</style>
