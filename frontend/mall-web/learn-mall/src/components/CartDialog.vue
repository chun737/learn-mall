<script setup lang="ts">
/**
 * 购物车弹窗：上方可滚动的商品列表 + 下方固定汇总/操作栏。
 *
 * 后端接口（CartController，均需登录）：
 *   GET    /cart                      列表（CartVO：汇总 + 明细）
 *   PUT    /cart/items/{id}           改数量
 *   PUT    /cart/items/{id}/checked   勾选单项
 *   PUT    /cart/checked              全选/取消全选
 *   DELETE /cart                      清空购物车
 *   DELETE /cart/items/{id}           删除单项
 *
 * 「清空」为什么要二次确认：这是不可撤销的批量删除，
 * 用一个确认框让用户明确"选择是否清空"，误点也不会把车清光。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { specsText } from '@/utils/text'
import {
  clearCart,
  getCart,
  removeCartItem,
  removeCheckedCartItems,
  updateCartChecked,
  updateCartCheckedAll,
  updateCartQuantity,
  type CartItem,
} from '@/api/cart'

const emit = defineEmits<{ (e: 'close'): void }>()

const router = useRouter()

const loading = ref(false)
const busyId = ref<number | null>(null)   // 正在改动的行：禁用它的按钮，防连点
const clearing = ref(false)

// 汇总字段单独存，避免 items 为空时还要到处判空
const items = ref<CartItem[]>([])
const totalQuantity = ref(0)
const checkedQuantity = ref(0)
const checkedAmount = ref(0)

/** 是否全部勾选（购物车为空时不算"全选"） */
const allChecked = computed(
  () => items.value.length > 0 && items.value.every((i) => i.checked === 1),
)
/** 部分勾选：el-checkbox 的"半选"状态 */
const someChecked = computed(() => {
  const n = items.value.filter((i) => i.checked === 1).length
  return n > 0 && n < items.value.length
})

/** 拉取购物车（改数量/勾选/删除/清空后都重新拉，以服务端为准） */
async function load() {
  loading.value = true
  try {
    const data = await getCart()
    items.value = data?.items ?? []
    totalQuantity.value = data?.totalQuantity ?? 0
    checkedQuantity.value = data?.checkedQuantity ?? 0
    checkedAmount.value = Number(data?.checkedAmount ?? 0)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '购物车加载失败')
  } finally {
    loading.value = false
  }
}

/** 用 SKU 图，没有则回退商品主图 */
function imgOf(item: CartItem) {
  return item.skuImage || item.mainImage || ''
}

/** 改数量：±1，边界是「不小于 1」和「不超过库存」 */
async function changeQty(item: CartItem, delta: number) {
  const next = item.quantity + delta
  if (next < 1) return
  if (next > item.stock) {
    ElMessage.warning(`库存仅剩 ${item.stock} 件`)
    return
  }
  busyId.value = item.id
  try {
    await updateCartQuantity(item.id, next)
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '修改数量失败')
  } finally {
    busyId.value = null
  }
}

/** 勾选 / 取消勾选单项 */
async function toggleChecked(item: CartItem, v: unknown) {
  busyId.value = item.id
  try {
    await updateCartChecked(item.id, v ? 1 : 0)
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    busyId.value = null
  }
}

/** 全选 / 取消全选 */
async function toggleAll(v: unknown) {
  try {
    await updateCartCheckedAll(v ? 1 : 0)
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

/** 删除单项，同样二次确认 */
async function askRemoveItem(item: CartItem) {
  try {
    await ElMessageBox.confirm(`确定把「${item.productName}」移出购物车吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消',
    })
  } catch {
    return                                  // 用户取消
  }
  try {
    await removeCartItem(item.id)
    ElMessage.success('已移出购物车')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

/** ⭐ 清空购物车：先让用户"选择是否清空"，确认后才真的删 */
async function askClear() {
  if (!items.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定清空购物车里的全部 ${totalQuantity.value} 件商品吗？此操作不可撤销。`,
      '清空购物车',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '再想想' },
    )
  } catch {
    return                                  // 用户选了"再想想" → 什么都不做
  }

  clearing.value = true
  try {
    await clearCart()
    ElMessage.success('购物车已清空')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '清空失败')
  } finally {
    clearing.value = false
  }
}

/** 删除已勾选的项（配合勾选使用，比清空更精准） */
async function askRemoveChecked() {
  if (!checkedQuantity.value) {
    ElMessage.warning('请先勾选要删除的商品')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除已勾选的 ${checkedQuantity.value} 件商品吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await removeCheckedCartItems()
    ElMessage.success('已删除勾选的商品')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

/** 去结算：先校验有勾选，再关弹窗 → 跳结算页 */
function goCheckout() {
  if (!checkedQuantity.value) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  emit('close')
  router.push('/checkout')
}

onMounted(load)
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <div class="dialog">
      <div class="head">
        <span class="title">
          我的购物车
          <small v-if="totalQuantity">共 {{ totalQuantity }} 件</small>
        </span>
        <button class="close" @click="emit('close')">×</button>
      </div>

      <!-- ===== 中间：可滚动的商品列表 ===== -->
      <div class="body">
        <p v-if="loading && !items.length" class="hint">加载中…</p>

        <div v-else-if="!items.length" class="empty">
          <p class="empty-txt">购物车还是空的</p>
          <button class="empty-btn" @click="emit('close')">去逛逛</button>
        </div>

        <ul v-else class="list">
          <li
            v-for="item in items"
            :key="item.id"
            class="row"
            :class="{ off: item.checked !== 1 }"
          >
            <el-checkbox
              class="pick"
              :model-value="item.checked === 1"
              :disabled="busyId === item.id"
              @change="(v: unknown) => toggleChecked(item, v)"
            />

            <div class="thumb">
              <img v-if="imgOf(item)" :src="imgOf(item)" :alt="item.productName" />
              <span v-else class="thumb-ph">无图</span>
            </div>

            <div class="mid">
              <p class="name">{{ item.productName }}</p>
              <p v-if="specsText(item.specs)" class="specs">{{ specsText(item.specs) }}</p>

              <div class="line">
                <span class="unit">¥{{ Number(item.price).toFixed(2) }}</span>

                <div class="qty">
                  <button
                    class="q-btn"
                    :disabled="item.quantity <= 1 || busyId === item.id"
                    @click="changeQty(item, -1)"
                  >
                    −
                  </button>
                  <span class="q-num">{{ item.quantity }}</span>
                  <button
                    class="q-btn"
                    :disabled="item.quantity >= item.stock || busyId === item.id"
                    @click="changeQty(item, 1)"
                  >
                    +
                  </button>
                </div>
              </div>
            </div>

            <div class="right">
              <span class="subtotal">
                ¥{{ (Number(item.price) * item.quantity).toFixed(2) }}
              </span>
              <button class="del" @click="askRemoveItem(item)">移除</button>
            </div>
          </li>
        </ul>

        <p v-if="items.length" class="scroll-tip">上下滚动可查看全部 {{ items.length }} 种商品</p>
      </div>

      <!-- ===== 底部：固定的汇总 + 操作栏 ===== -->
      <div class="foot">
        <div class="foot-top">
          <el-checkbox
            :model-value="allChecked"
            :indeterminate="someChecked"
            :disabled="!items.length"
            @change="(v: unknown) => toggleAll(v)"
          >
            全选
          </el-checkbox>
          <span class="sum">
            已选 <b>{{ checkedQuantity }}</b> 件，合计
            <em>¥{{ checkedAmount.toFixed(2) }}</em>
          </span>
        </div>

        <div class="foot-bottom">
          <button class="btn ghost" :disabled="!checkedQuantity" @click="askRemoveChecked">
            删除已选
          </button>
          <button
            class="btn danger"
            :disabled="!items.length || clearing"
            @click="askClear"
          >
            {{ clearing ? '清空中…' : '清空购物车' }}
          </button>
          <button class="btn main" :disabled="!checkedQuantity" @click="goCheckout">
            去结算({{ checkedQuantity }})
          </button>
        </div>
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
  z-index: 110;
}
.dialog {
  width: 94%;
  max-width: 560px;
  max-height: 86vh;          /* 商品多时整体不超一屏 */
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}
.head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.title {
  font-size: 15px;
  font-weight: 700;
}
.title small {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #999;
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

/* 列表区自己滚，头尾固定 */
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 16px;
  background: #fafafa;
}
.hint {
  margin: 0;
  padding: 50px 0;
  text-align: center;
  font-size: 13px;
  color: #999;
}

/* 空购物车 */
.empty {
  padding: 50px 0;
  text-align: center;
}
.empty-txt {
  margin: 0 0 14px;
  font-size: 14px;
  color: #999;
}
.empty-btn {
  padding: 8px 24px;
  font-size: 14px;
  color: #fff;
  background: #e1251b;
  border: none;
  border-radius: 18px;
  cursor: pointer;
}

/* ===== 商品行 ===== */
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}
/* 未勾选的行整体变淡，一眼看得出哪些不参与结算 */
.row.off {
  opacity: 0.6;
}
.pick {
  flex-shrink: 0;
  margin-top: 18px;
}
.thumb {
  flex-shrink: 0;
  width: 68px;
  height: 68px;
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
.line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.unit {
  font-size: 13px;
  color: #e1251b;
  font-weight: 700;
}

/* 数量步进器 */
.qty {
  display: flex;
  align-items: center;
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  overflow: hidden;
}
.q-btn {
  width: 26px;
  height: 24px;
  border: none;
  background: #fafafa;
  color: #333;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}
.q-btn:hover:not(:disabled) {
  background: #f0f0f0;
  color: #e1251b;
}
.q-btn:disabled {
  color: #ccc;
  cursor: not-allowed;
}
.q-num {
  min-width: 32px;
  text-align: center;
  font-size: 13px;
  border-left: 1px solid #e5e5e5;
  border-right: 1px solid #e5e5e5;
  line-height: 24px;
}

.right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  height: 68px;
}
.subtotal {
  font-size: 14px;
  font-weight: 700;
  color: #e1251b;
}
.del {
  border: none;
  background: none;
  font-size: 12px;
  color: #999;
  cursor: pointer;
  padding: 0;
}
.del:hover {
  color: #e1251b;
}

.scroll-tip {
  margin: 12px 0 0;
  text-align: center;
  font-size: 11px;
  color: #bbb;
}

/* ===== 底部汇总栏 ===== */
.foot {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;      /* 上下两行：上行汇总、下行操作 */
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
.foot-top {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.sum {
  font-size: 13px;
  color: #666;
}
.sum b {
  color: #333;
}
.sum em {
  font-style: normal;
  font-size: 17px;
  font-weight: 700;
  color: #e1251b;
}
.foot-bottom {
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 「去结算」是主操作，用 margin-left:auto 推到最右，
   和左边的删除类操作隔开，减少误点 */
.btn.main {
  margin-left: auto;
  padding: 8px 20px;
  font-weight: 700;
  background: #e1251b;
  border-color: #e1251b;
  color: #fff;
}
.btn.main:hover:not(:disabled) {
  background: #c91f17;
}
.btn {
  padding: 8px 16px;
  font-size: 13px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid #ddd;
  background: #fff;
  color: #333;
}
.btn.ghost:hover:not(:disabled) {
  border-color: #e1251b;
  color: #e1251b;
}
.btn.danger {
  background: #e1251b;
  border-color: #e1251b;
  color: #fff;
}
.btn.danger:hover:not(:disabled) {
  background: #c91f17;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
