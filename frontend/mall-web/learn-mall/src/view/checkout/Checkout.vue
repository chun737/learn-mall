<script setup lang="ts">
/**
 * 结算页 /checkout
 *
 * 两步数据流：
 *   ① GET /order/preview → 收货地址列表 + 已勾选商品清单 + 金额汇总
 *   ② POST /order        → 提交订单，拿到订单号
 *
 * 为什么做成独立页面，而不是购物车弹窗里的第二层：
 *   地址选择、商品清单、金额、备注、成功结果这些内容塞进弹窗会非常挤；
 *   而且结算过程中用户可能跳去加地址、再返回，独立页面（有自己 URL）更顺。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  createOrder,
  getOrderPreview,
  type OrderAmount,
  type OrderPreviewItem,
} from '@/api/order'
import { getProductDetail } from '@/api/product'
import type { Address } from '@/api/address'
import { isLoggedIn } from '@/utils/auth'
import { specsText } from '@/utils/text'
import AddressDialog from '@/components/AddressDialog.vue'
import PayDialog from '@/components/PayDialog.vue'

const route = useRoute()
const router = useRouter()

// ===== 直购模式：详情页「立即购买」会把这三个参数带过来 =====
// 带 skuId 时只结算这一个规格（清单来自商品详情）；不带时按购物车勾选项结算。
const directSkuId = computed(() => {
  const v = Number(route.query.skuId)
  return Number.isFinite(v) && v > 0 ? v : null
})
const directProductId = computed(() => {
  const v = Number(route.query.productId)
  return Number.isFinite(v) && v > 0 ? v : null
})
const directQuantity = computed(() => {
  const v = Number(route.query.quantity)
  return Number.isFinite(v) && v > 0 ? v : 1
})
/** 是否直购模式（只买详情页当前选中的这一个规格） */
const isDirect = computed(() => directSkuId.value !== null && directProductId.value !== null)

const loading = ref(true)
const submitting = ref(false)
const errorMsg = ref('')

const addresses = ref<Address[]>([])
const items = ref<OrderPreviewItem[]>([])
const amount = ref<OrderAmount>({ totalAmount: 0, freightAmount: 0, payAmount: 0 })

/** 选中的收货地址 ID */
const addressId = ref<number | null>(null)
const remark = ref('')

/** 收货地址弹窗开关（结算页里可以顺手加地址） */
const addrOpen = ref(false)

/** 下单成功后整页切成成功态，避免用户重复提交 */
const done = ref<{ orderNo: string; payAmount: number } | null>(null)
/** 收银台开关 */
const payOpen = ref(false)
/** 是否已支付成功（成功页据此切换文案与按钮） */
const paid = ref(false)

/** 拉取结算信息 */
async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const p = await getOrderPreview()
    addresses.value = p.addresses ?? []

    if (isDirect.value) {
      // ⭐ 直购模式：清单来自商品详情里选中的那个规格，而不是购物车勾选项。
      //   地址仍用 preview 的（与购物车无关），运费也沿用后端算出的规则值。
      await loadDirectItems(Number(p.amount?.freightAmount ?? 0))
    } else {
      items.value = p.items ?? []
      amount.value = p.amount ?? { totalAmount: 0, freightAmount: 0, payAmount: 0 }
    }

    // 用户之前选的地址若还在列表里就保持不变
    //（比如刚在地址弹窗里新增了一条，重拉后不该把已选地址重置掉）
    if (!addresses.value.some((a) => a.id === addressId.value)) {
      const def = addresses.value.find((a) => a.isDefault === 1) ?? addresses.value[0]
      addressId.value = def ? def.id : null
    }
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '结算信息加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 直购模式：从商品详情里取出目标 SKU，构造一条「只有它」的清单。
 *
 * ⚠️ 这里算出的金额**只用于页面展示**：真实应付金额以后端 POST /order 返回的
 *   payAmount 为准（后端会按实时价格重新计算），所以前端即使算得不准也不会下错单。
 */
async function loadDirectItems(freight: number) {
  const detail = await getProductDetail(directProductId.value as number)
  const sku = (detail.skus ?? []).find((s) => Number(s.id) === directSkuId.value)
  if (!sku) {
    errorMsg.value = '该规格已下架，请回商品页重新选择'
    items.value = []
    return
  }

  const qty = directQuantity.value
  items.value = [
    {
      cartId: 0,                 // 直购没有购物车项，占位即可（后端不使用这个字段）
      productId: detail.id,
      productName: detail.productName,
      mainImage: detail.mainImage,
      skuId: Number(sku.id),
      specs: sku.specs,
      price: Number(sku.price),
      quantity: qty,
      stock: sku.stock,
    },
  ]

  const total = Number(sku.price) * qty
  amount.value = {
    totalAmount: total,
    freightAmount: freight,
    payAmount: total + freight,
  }
}

/** 地址弹窗关闭：地址可能新增了，重新拉一次预览 */
function onAddressClosed() {
  addrOpen.value = false
  load()
}

/** 提交订单 */
async function submit() {
  const aid = addressId.value
  if (!aid) {
    ElMessage.warning('请先选择收货地址')
    return
  }
  if (!items.value.length) {
    ElMessage.warning('没有可结算的商品')
    return
  }

  submitting.value = true
  try {
    const res = await createOrder({
      addressId: aid,
      remark: remark.value.trim() || undefined,
      // 明细以「页面所见」为准显式提交，不让后端去读购物车勾选项——
      // 否则提交那一瞬间购物车被改动（比如另一个标签页操作），就会下错单
      skuList: items.value.map((i) => ({ skuId: i.skuId, quantity: i.quantity })),
    })
    done.value = { orderNo: res.orderNo, payAmount: Number(res.payAmount) }

    // ⭐ 下单成功立刻调出收银台（模拟支付页）。
    //   电商的标准动线就是"下单即付款"，不让用户在下单成功页和「去支付」按钮之间多点一次。
    //   关掉收银台也没关系——成功页上仍保留「去支付」，随时能再进来。
    payOpen.value = true
    ElMessage.success('下单成功，正在打开收银台')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '下单失败')
  } finally {
    submitting.value = false
  }
}

/**
 * 支付完成后收银台该跳回哪：回到本页并带上 paid=1。
 * 页面重新挂载时会读这几个参数直接进「支付成功」态（见 onMounted）。
 *
 * 为什么要用 URL 而不是"付完再通知组件"：
 *   点「确认支付」会 window.location.assign 跳到收银台，整个 SPA 会卸载，
 *   组件实例、props、emit 全部失效——只有 URL 能跨越这次"跳出去再跳回来"。
 */
const payReturnUrl = computed(() =>
  done.value
    ? `/checkout?paid=1&orderNo=${encodeURIComponent(done.value.orderNo)}&amount=${done.value.payAmount}`
    : '/orders',
)

/** 金额统一保留两位小数 */
function money(v: unknown) {
  return Number(v || 0).toFixed(2)
}

onMounted(() => {
  // 结算必须登录：直接贴 URL 进来也要拦住（带 redirect，登录后自动回来）
  if (!isLoggedIn()) {
    router.replace({ path: '/login', query: { redirect: '/checkout' } })
    return
  }

  // ⭐ 从模拟收银台付完款跳回来了（/checkout?paid=1&orderNo=...&amount=...）：
  //   用户此刻只想看到"支付成功"，没必要再拉一次结算数据——
  //   购物车已在下单时清空，拉了也只会显示"没有可结算的商品"。
  if (route.query.paid === '1') {
    done.value = {
      orderNo: String(route.query.orderNo ?? ''),
      payAmount: Number(route.query.amount ?? 0),
    }
    paid.value = true
    loading.value = false
    ElMessage.success('支付成功')
    return
  }

  load()
})
</script>

<template>
  <div class="page">
    <header class="head">
      <button class="back" @click="router.push('/')">← 返回首页</button>
      <h1 class="title">确认订单</h1>
    </header>

    <!-- ===== 下单成功 ===== -->
    <section v-if="done" class="done">
      <div class="done-icon" :class="{ ok: paid }">{{ paid ? '✓' : '!' }}</div>
      <h2 class="done-title">{{ paid ? '支付成功' : '下单成功' }}</h2>
      <p class="done-line">订单号：<b>{{ done.orderNo }}</b></p>
      <p class="done-line">
        应付金额：<em>¥{{ money(done.payAmount) }}</em>
      </p>
      <p class="done-tip">
        {{ paid ? '订单已支付，等待商家发货' : '订单已创建，当前状态为待支付' }}
      </p>

      <div class="done-btns">
        <button v-if="!paid" class="btn main" @click="payOpen = true">
          去支付 ¥{{ money(done.payAmount) }}
        </button>
        <button class="btn" @click="router.push('/orders')">查看订单</button>
        <button class="btn" @click="router.push('/')">继续购物</button>
      </div>
    </section>

    <!-- ===== 正常结算流程 ===== -->
    <template v-else>
      <p v-if="loading" class="hint">加载中…</p>
      <p v-else-if="errorMsg" class="hint err">{{ errorMsg }}</p>

      <div v-else class="content">
        <!-- ① 收货地址 -->
        <section class="card">
          <h3 class="card-title">
            收货地址
            <button class="mini" @click="addrOpen = true">+ 新增地址</button>
          </h3>

          <p v-if="!addresses.length" class="empty">
            还没有收货地址，请先
            <a class="link" @click="addrOpen = true">新增一个</a>
          </p>

          <div v-else class="addr-list">
            <div
              v-for="a in addresses"
              :key="a.id"
              class="addr"
              :class="{ on: addressId === a.id }"
              @click="addressId = a.id"
            >
              <span class="dot" />
              <div class="addr-body">
                <p class="addr-line1">
                  <b>{{ a.receiverName }}</b>
                  <span class="phone">{{ a.receiverPhone }}</span>
                  <em v-if="a.isDefault === 1" class="def">默认</em>
                </p>
                <p class="addr-line2">
                  {{ a.province }} {{ a.city }} {{ a.district }} {{ a.detailAddress }}
                </p>
              </div>
            </div>
          </div>
        </section>

        <!-- ② 商品清单 -->
        <section class="card">
          <h3 class="card-title">
            商品清单
            <small v-if="items.length">共 {{ items.length }} 种</small>
          </h3>

          <p v-if="!items.length" class="empty">
            没有可结算的商品，请回购物车勾选后再来
          </p>

          <ul v-else class="list">
            <li v-for="it in items" :key="it.cartId" class="row">
              <div class="thumb">
                <img v-if="it.mainImage" :src="it.mainImage" :alt="it.productName" />
                <span v-else class="thumb-ph">无图</span>
              </div>
              <div class="mid">
                <p class="name">{{ it.productName }}</p>
                <p v-if="specsText(it.specs)" class="specs">{{ specsText(it.specs) }}</p>
                <p class="qty">× {{ it.quantity }}</p>
              </div>
              <span class="sub">¥{{ money(Number(it.price) * it.quantity) }}</span>
            </li>
          </ul>
        </section>

        <!-- ③ 订单备注 -->
        <section class="card">
          <h3 class="card-title">订单备注</h3>
          <textarea
            v-model="remark"
            class="remark"
            maxlength="200"
            placeholder="选填，如对配送时间的要求"
          />
        </section>

        <!-- ④ 金额汇总 -->
        <section class="card">
          <div class="amt-line">
            <span>商品总额</span>
            <span>¥{{ money(amount.totalAmount) }}</span>
          </div>
          <div class="amt-line">
            <span>运费</span>
            <span>¥{{ money(amount.freightAmount) }}</span>
          </div>
          <div class="amt-line pay">
            <span>应付金额</span>
            <em>¥{{ money(amount.payAmount) }}</em>
          </div>
        </section>
      </div>

      <!-- ⑤ 吸底提交栏：滚到哪儿都能提交 -->
      <div v-if="!loading && !errorMsg" class="bar">
        <span class="bar-sum">
          应付：<em>¥{{ money(amount.payAmount) }}</em>
        </span>
        <button
          class="btn main"
          :disabled="submitting || !addressId || !items.length"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </div>
    </template>

    <!-- 收货地址弹窗：加完地址关闭后会自动重拉预览 -->
    <AddressDialog v-if="addrOpen" @close="onAddressClosed" />

    <!-- 收银台：下单成功后自动打开；点「确认支付」会跳转到模拟收银台 -->
    <PayDialog
      v-if="payOpen && done"
      :order-no="done.orderNo"
      :amount="done.payAmount"
      :return-url="payReturnUrl"
      @close="payOpen = false"
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

/* ===== 顶部 ===== */
.head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 0;
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

.hint {
  margin: 0;
  padding: 80px 0;
  text-align: center;
  font-size: 14px;
  color: #999;
}
.hint.err {
  color: #c0392b;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ===== 卡片 ===== */
.card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
}
.card-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-title small {
  font-size: 12px;
  font-weight: 400;
  color: #999;
}
.mini {
  margin-left: auto;
  padding: 4px 10px;
  font-size: 12px;
  color: #e1251b;
  background: #fff;
  border: 1px solid #e1251b;
  border-radius: 12px;
  cursor: pointer;
}
.mini:hover {
  background: #fff5f4;
}
.empty {
  margin: 0;
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: #999;
}
.link {
  color: #e1251b;
  cursor: pointer;
}

/* ===== 地址列表 ===== */
.addr-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.addr {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.addr:hover {
  border-color: #f5b7b3;
}
.addr.on {
  border-color: #e1251b;
  background: #fffafa;
}
/* 单选圆点：选中的填充红色 */
.dot {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-top: 3px;
  border: 1px solid #ccc;
  border-radius: 50%;
  position: relative;
}
.addr.on .dot {
  border-color: #e1251b;
}
.addr.on .dot::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e1251b;
}
.addr-body {
  flex: 1;
  min-width: 0;
}
.addr-line1 {
  margin: 0;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.phone {
  font-size: 13px;
  color: #666;
}
.def {
  font-style: normal;
  font-size: 11px;
  color: #e1251b;
  background: #fdecea;
  border-radius: 10px;
  padding: 2px 8px;
}
.addr-line2 {
  margin: 6px 0 0;
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}

/* ===== 商品清单 ===== */
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
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
.mid {
  flex: 1;
  min-width: 0;
}
.name {
  margin: 0;
  font-size: 13px;
  line-height: 1.4;
  color: #333;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.specs {
  margin: 4px 0 0;
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.qty {
  margin: 4px 0 0;
  font-size: 12px;
  color: #999;
}
.sub {
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 700;
  color: #e1251b;
}

/* ===== 备注 ===== */
.remark {
  width: 100%;
  box-sizing: border-box;
  min-height: 64px;
  padding: 10px 12px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.5;
  border: 1px solid #ddd;
  border-radius: 6px;
  outline: none;
  resize: vertical;
}
.remark:focus {
  border-color: #e1251b;
}

/* ===== 金额 ===== */
.amt-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #666;
  padding: 6px 0;
}
.amt-line.pay {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
  font-size: 14px;
  color: #333;
}
.amt-line.pay em {
  font-style: normal;
  font-size: 20px;
  font-weight: 700;
  color: #e1251b;
}

/* ===== 吸底提交栏 ===== */
.bar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 12px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.06);
}
.bar-sum {
  font-size: 13px;
  color: #666;
}
.bar-sum em {
  font-style: normal;
  font-size: 18px;
  font-weight: 700;
  color: #e1251b;
}

.btn {
  padding: 10px 26px;
  font-size: 14px;
  font-weight: 700;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid #ddd;
  background: #fff;
  color: #333;
}
.btn.main {
  background: #e1251b;
  border-color: #e1251b;
  color: #fff;
}
.btn.main:hover:not(:disabled) {
  background: #c91f17;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn.wide {
  width: 200px;
}
.done-btns {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}
/* 下单成功是「!」（橙色提示待付款），支付成功才是✓（红底） */
.done-icon.ok {
  background: #38a169;
}

/* ===== 下单成功 ===== */
.done {
  background: #fff;
  border-radius: 10px;
  padding: 50px 20px;
  text-align: center;
}
.done-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: #e1251b;
  color: #fff;
  font-size: 30px;
  line-height: 56px;
}
.done-title {
  margin: 0 0 16px;
  font-size: 19px;
  font-weight: 700;
}
.done-line {
  margin: 6px 0;
  font-size: 14px;
  color: #666;
}
.done-line b {
  color: #333;
}
.done-line em {
  font-style: normal;
  font-size: 18px;
  font-weight: 700;
  color: #e1251b;
}
.done-tip {
  margin: 14px 0 22px;
  font-size: 12px;
  color: #999;
}
</style>
