<script setup lang="ts">
/**
 * 收银台弹窗：选支付方式 → 跳转到支付渠道的收银台。
 *
 * ⭐ 关键：这个组件【不负责付款】，它只做两件事——
 *   ① 调 POST /payment 建支付单，拿到渠道给的跳转地址 payParams.alipayTradePagePay
 *   ② window.location.assign 跳过去，把"付款"这件事交给那个页面
 *
 *   本地演示：地址是 MockPayChannelServiceImpl 给的 /mock-pay.html?...（模拟收银台）
 *   真实接入：地址会是支付宝的收银台
 *   —— 两种情况下【本组件代码一行都不用改】，这正是 PayChannelService 抽象的价值。
 *
 * 付款完成后怎么回来：
 *   跳回地址写进 sessionStorage['mockPay:return']（同源，收银台页面读得到）。
 *   跳转后 SPA 已卸载，props/emit 都失效了，只能靠存储中转。
 *
 * 用法：<PayDialog :order-no="..." :amount="..." :return-url="..." @close="..." />
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createPay, PAY_TYPES } from '@/api/payment'

const props = defineProps<{
  orderNo: string
  amount: number
  /** 支付完成后跳回的地址；不传则默认回结算页展示「支付成功」 */
  returnUrl?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const payType = ref<number>(PAY_TYPES[0].value)
/** 当前支付方式的中文名，用在"正在跳转到 XX 收银台"的文案里 */
const payingLabel = computed(
  () => PAY_TYPES.find((p) => p.value === payType.value)?.label ?? '第三方',
)
/** choose=选支付方式  paying=正在跳转到模拟收银台 */
const step = ref<'choose' | 'paying'>('choose')

/**
 * 点「确认支付」：建支付单 → 跳转到支付渠道给的收银台。
 *
 * ⭐ 付款动作不在这里做，而是"跳出去"完成的：
 *   ① POST /payment 建支付单，后端通过 PayChannelService 返回跳转地址
 *      （本地 = MockPayChannelServiceImpl 的 /mock-pay.html?...）
 *   ② window.location.assign 真跳转 —— 当前 SPA 整个卸载
 *   ③ 用户在模拟收银台点「确认付款」，那个页面调 /payment/mock/pay
 *   ④ 它再跳回我们给的 returnUrl，由结算页 / 订单页展示支付成功
 *
 *   这么拆的意义：真实接入支付宝时，第 ② 步会跳到支付宝收银台，
 *   第 ③ 步是用户在支付宝里操作 —— 前端代码一行都不用改。
 */
async function pay() {
  step.value = 'paying'
  try {
    const created = await createPay({ orderNo: props.orderNo, payType: payType.value })

    // 支付渠道给的前端跳转地址（本地 = 模拟收银台；真实 = 第三方收银台）
    const url = created.payParams?.alipayTradePagePay
    if (!url) {
      throw new Error('支付渠道没有返回跳转地址')
    }

    // 付完款要跳回哪：写进 sessionStorage。跳转后 SPA 会卸载，
    // props 传不过去，只能靠这个（同源）存储中转给收银台页面。
    const backTo =
      props.returnUrl ??
      `/checkout?paid=1&orderNo=${encodeURIComponent(props.orderNo)}&amount=${props.amount}`
    sessionStorage.setItem('mockPay:return', backTo)

    // ⭐ 真·跳转：把"付款"交给收银台页面
    window.location.assign(url)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发起支付失败')
    step.value = 'choose'
  }
}

function money(v: unknown) {
  return Number(v || 0).toFixed(2)
}
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <div class="dialog">
      <div class="head">
        <span class="title">
          收银台
          <em class="mock-badge">模拟</em>
        </span>
        <button v-if="step !== 'paying'" class="close" @click="emit('close')">×</button>
      </div>

      <!-- ===== 选支付方式 ===== -->
      <div v-if="step === 'choose'" class="body">
        <div class="amount-box">
          <p class="amount-label">应付金额</p>
          <p class="amount"><i>¥</i>{{ money(amount) }}</p>
          <p class="order-no">订单号 {{ orderNo }}</p>
        </div>

        <p class="section">选择支付方式</p>
        <ul class="pays">
          <li
            v-for="p in PAY_TYPES"
            :key="p.value"
            class="pay"
            :class="{ on: payType === p.value }"
            @click="payType = p.value"
          >
            <span class="dot" />
            <span class="pay-label">{{ p.label }}</span>
          </li>
        </ul>

        <p class="hint">
          本地演示环境没有接真实的第三方收银台，点下方按钮会直接模拟「付款成功」。
        </p>

        <button class="submit" @click="pay">确认支付 ¥{{ money(amount) }}</button>
      </div>

      <!-- ===== 跳转中 ===== -->
      <div v-else class="body center">
        <div class="spinner" />
        <p class="paying-txt">正在跳转到「{{ payingLabel }}」收银台…</p>
        <p class="sub-txt">跳转后将由收银台页面完成付款，付完自动返回</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 120;               /* 比购物车/地址弹窗(110)更靠上：收银台可能在它们之上打开 */
}
.dialog {
  width: 92%;
  max-width: 420px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
}
/* 「模拟」标识：一眼表明这是演示收银台，不是真实第三方页面 */
.mock-badge {
  font-style: normal;
  font-size: 11px;
  font-weight: 400;
  color: #e1251b;
  background: #fdecea;
  border-radius: 10px;
  padding: 2px 8px;
}
.sub-txt {
  margin: 0 0 8px;
  font-size: 12px;
  color: #bbb;
}
.close {
  border: none;
  background: none;
  font-size: 20px;
  line-height: 1;
  color: #999;
  cursor: pointer;
}
.close:hover {
  color: #e1251b;
}

.body {
  padding: 18px 16px 20px;
}
.body.center {
  text-align: center;
}

/* ===== 金额区 ===== */
.amount-box {
  text-align: center;
  padding-bottom: 16px;
  border-bottom: 1px solid #f5f5f5;
}
.amount-label {
  margin: 0;
  font-size: 12px;
  color: #999;
}
.amount {
  margin: 6px 0 8px;
  font-size: 32px;
  font-weight: 700;
  color: #e1251b;
  line-height: 1.1;
}
.amount i {
  font-style: normal;
  font-size: 18px;
}
.order-no {
  margin: 0;
  font-size: 12px;
  color: #aaa;
  word-break: break-all;
}

.section {
  margin: 16px 0 8px;
  font-size: 13px;
  color: #666;
}

/* ===== 支付方式 ===== */
.pays {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pay {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border: 1px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.pay:hover {
  border-color: #f5b7b3;
}
.pay.on {
  border-color: #e1251b;
  background: #fffafa;
}
/* 单选圆点 */
.dot {
  width: 16px;
  height: 16px;
  border: 1px solid #ccc;
  border-radius: 50%;
  position: relative;
  flex-shrink: 0;
}
.pay.on .dot {
  border-color: #e1251b;
}
.pay.on .dot::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e1251b;
}
.pay-label {
  font-size: 14px;
  color: #333;
}

.hint {
  margin: 14px 0 0;
  font-size: 12px;
  color: #aaa;
  line-height: 1.6;
}

.submit {
  width: 100%;
  height: 44px;
  margin-top: 16px;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  background: #e1251b;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}
.submit:hover {
  background: #c91f17;
}

/* ===== 付款中 ===== */
.spinner {
  width: 34px;
  height: 34px;
  margin: 20px auto 14px;
  border: 3px solid #f0f0f0;
  border-top-color: #e1251b;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.paying-txt {
  margin: 0 0 8px;
  font-size: 14px;
  color: #666;
}

/* ===== 支付成功 ===== */
.ok-icon {
  width: 54px;
  height: 54px;
  margin: 14px auto 14px;
  border-radius: 50%;
  background: #e1251b;
  color: #fff;
  font-size: 28px;
  line-height: 54px;
}
.ok-title {
  margin: 0 0 14px;
  font-size: 18px;
  font-weight: 700;
}
.ok-line {
  margin: 0 0 8px;
  font-size: 14px;
  color: #666;
}
.ok-line em {
  font-style: normal;
  font-size: 20px;
  font-weight: 700;
  color: #e1251b;
}
.submit.done {
  margin-top: 20px;
}
</style>
