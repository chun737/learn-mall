<script setup lang="ts">
/**
 * 收货地址弹窗：上半是地址列表，下半是新增表单，长按卡片可删除。
 *
 * 后端接口（UserController，均需登录）：
 *   GET    /user/addresses                 列表（返回数组）
 *   POST   /user/addresses                 新增
 *   PUT    /user/addresses/{id}/default    设为默认
 *   DELETE /user/addresses/{id}            删除
 *
 * 为什么删除用"长按"而不是放个删除按钮：移动端误触率高，
 * 长按是一个"有成本"的操作，能显著降低误删概率。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addAddress,
  deleteAddress,
  listAddresses,
  setDefaultAddress,
  type Address,
  type AddressParams,
} from '@/api/address'

const emit = defineEmits<{ (e: 'close'): void }>()

const loading = ref(false)
const submitting = ref(false)
const list = ref<Address[]>([])

/** 新增表单（与地址列表互不干扰） */
const form = reactive<AddressParams>({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  isDefault: 0,
})

/** el-checkbox 用布尔值，这里做一层 0/1 ↔ boolean 的换算 */
const isDefaultChecked = computed({
  get: () => form.isDefault === 1,
  set: (v: boolean) => (form.isDefault = v ? 1 : 0),
})

async function load() {
  loading.value = true
  try {
    list.value = (await listAddresses()) ?? []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '地址加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.receiverName = ''
  form.receiverPhone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detailAddress = ''
  form.isDefault = 0
}

/** 新增地址：前端先校验，再提交 */
async function submit() {
  if (!form.receiverName.trim()) {
    ElMessage.warning('请填写收货人姓名')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  if (!form.province.trim() || !form.city.trim() || !form.district.trim()) {
    ElMessage.warning('请填写完整的省 / 市 / 区')
    return
  }
  if (!form.detailAddress.trim()) {
    ElMessage.warning('请填写详细地址')
    return
  }

  submitting.value = true
  try {
    await addAddress({
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone.trim(),
      province: form.province.trim(),
      city: form.city.trim(),
      district: form.district.trim(),
      detailAddress: form.detailAddress.trim(),
      isDefault: form.isDefault,
    })
    ElMessage.success('地址添加成功')
    resetForm()
    await load()                    // 重新拉列表：以服务端为准，不做本地拼接
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '添加失败')
  } finally {
    submitting.value = false
  }
}

/** 设为默认（已是默认就不重复请求） */
async function makeDefault(item: Address) {
  if (item.isDefault === 1) return
  try {
    await setDefaultAddress(item.id)
    ElMessage.success('已设为默认地址')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

// ===== 长按删除 =====
const PRESS_MS = 600                  // 按住多久算长按
let pressTimer: ReturnType<typeof setTimeout> | undefined

/** 按下：开始计时 */
function pressStart(item: Address) {
  clearTimeout(pressTimer)
  pressTimer = setTimeout(() => {
    pressTimer = undefined
    askDelete(item)                   // 到时间 → 弹删除确认
  }, PRESS_MS)
}

/** 松手 / 移出 / 触摸取消：取消计时（没按够时间就当普通点击） */
function pressEnd() {
  clearTimeout(pressTimer)
  pressTimer = undefined
}

/** 删除确认 → 调接口 → 刷新列表 */
async function askDelete(item: Address) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.receiverName}」的收货地址吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return                            // 用户点了取消（ElMessageBox 取消会 reject）
  }

  try {
    await deleteAddress(item.id)
    ElMessage.success('地址已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

/** 拼成一行完整地址 */
function fullAddress(a: Address) {
  return `${a.province} ${a.city} ${a.district} ${a.detailAddress}`
}

onMounted(load)
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <div class="dialog">
      <div class="head">
        <span class="title">收货地址</span>
        <button class="close" @click="emit('close')">×</button>
      </div>

      <div class="body">
        <!-- ========== 上：地址列表 ========== -->
        <div class="section-title">
          已有地址
          <small v-if="list.length">（长按卡片可删除）</small>
        </div>

        <p v-if="loading" class="hint">加载中…</p>
        <p v-else-if="!list.length" class="hint">还没有收货地址，请在下方添加</p>

        <div v-else class="addr-list">
          <div
            v-for="a in list"
            :key="a.id"
            class="addr-card"
            :class="{ primary: a.isDefault === 1 }"
            @mousedown="pressStart(a)"
            @mouseup="pressEnd"
            @mouseleave="pressEnd"
            @touchstart.passive="pressStart(a)"
            @touchend="pressEnd"
            @touchcancel="pressEnd"
          >
            <div class="addr-top">
              <span class="name">{{ a.receiverName }}</span>
              <span class="phone">{{ a.receiverPhone }}</span>
              <span v-if="a.isDefault === 1" class="badge default">默认</span>
              <button v-else class="badge set" @click.stop="makeDefault(a)">设为默认</button>
            </div>
            <p class="addr-text">{{ fullAddress(a) }}</p>
          </div>
        </div>

        <!-- ========== 下：新增收货地址 ========== -->
        <div class="section-title add-title">新增收货地址</div>

        <div class="form">
          <div class="row row-2">
            <el-input v-model="form.receiverName" placeholder="收货人姓名" />
            <el-input
              v-model="form.receiverPhone"
              placeholder="手机号"
              maxlength="11"
            />
          </div>

          <div class="row row-3">
            <el-input v-model="form.province" placeholder="省" />
            <el-input v-model="form.city" placeholder="市" />
            <el-input v-model="form.district" placeholder="区 / 县" />
          </div>

          <div class="row">
            <el-input v-model="form.detailAddress" placeholder="详细地址（街道、门牌号）" />
          </div>

          <div class="row row-bottom">
            <el-checkbox v-model="isDefaultChecked">设为默认地址</el-checkbox>
            <el-button type="primary" :loading="submitting" @click="submit">
              新增地址
            </el-button>
          </div>
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
  width: 92%;
  max-width: 460px;
  max-height: 86vh;          /* 地址多时整体不超一屏 */
  display: flex;
  flex-direction: column;
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
  flex-shrink: 0;
}
.title {
  font-size: 15px;
  font-weight: 700;
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
/* 内容区自己滚，头部固定 */
.body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}

.section-title {
  font-size: 13px;
  font-weight: 700;
  color: #333;
  margin-bottom: 10px;
}
.section-title small {
  font-weight: 400;
  font-size: 12px;
  color: #aaa;
}
.add-title {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.hint {
  margin: 0 0 10px;
  font-size: 13px;
  color: #aaa;
}

/* ===== 地址卡片 ===== */
.addr-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.addr-card {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.1s;
  user-select: none;         /* 长按时不选中文字 */
  -webkit-user-select: none;
  -webkit-touch-callout: none;   /* 移动端长按不弹系统菜单 */
}
.addr-card:hover {
  border-color: #f5b7b3;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
/* 按下时的反馈：让用户感知"正在长按" */
.addr-card:active {
  transform: scale(0.99);
  border-color: #e1251b;
}
.addr-card.primary {
  border-color: #e1251b;
  background: #fffafa;
}
.addr-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.name {
  font-size: 14px;
  font-weight: 700;
  color: #333;
}
.phone {
  font-size: 13px;
  color: #666;
}
.badge {
  margin-left: auto;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}
.badge.default {
  background: #fdecea;
  color: #e1251b;
}
.badge.set {
  border: 1px solid #ddd;
  background: #fff;
  color: #888;
  cursor: pointer;
}
.badge.set:hover {
  border-color: #e1251b;
  color: #e1251b;
}
.addr-text {
  margin: 6px 0 0;
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}

/* ===== 新增表单 ===== */
.form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.row-2 {
  display: flex;
  gap: 10px;
}
.row-3 {
  display: flex;
  gap: 10px;
}
/* 三个小输入框等分宽度 */
.row-3 :deep(.el-input) {
  flex: 1;
  min-width: 0;
}
.row-2 :deep(.el-input) {
  flex: 1;
  min-width: 0;
}
.row-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
}
</style>
