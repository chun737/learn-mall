<script setup lang="ts">
/**
 * 个人信息弹窗：查看 + 修改 头像 / 昵称 / 手机号 / 邮箱 / 性别。
 *
 * 后端接口：
 *   GET  /user/profile        → UserProfileVO（打开时拉全量，回填表单）
 *   POST /user/upload/image   → 上传头像到 OSS，返回图片 URL（字段名固定 file）
 *   PUT  /user/profile        → UpdateProfileDTO（字段均可选，传了才更新）
 *
 * 头像的时序：选图 → 本地预览 → 点「保存」时才真正上传 OSS 并提交资料。
 *   为什么不在选图时就上传：用户很可能选完又点取消，那样 OSS 会攒下一堆
 *   没人引用的孤儿文件。攒到「保存」再传，取消就等于什么都没发生（零垃圾）。
 *
 * 用法：<ProfileDialog @close="..." @saved="..." />
 *   close：请求关闭弹窗
 *   saved：保存成功，携带后端返回的最新 UserProfile，父组件据此刷新顶部展示
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getProfile, updateProfile, uploadImage, type UserProfile } from '@/api/auth'

/** 与后端 OssService 的扩展名白名单 / 大小上限保持一致，避免白跑一次网络 */
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp']
const MAX_SIZE = 5 * 1024 * 1024

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved', profile: UserProfile): void
}>()

const loading = ref(true)
const saving = ref(false)
const errorMsg = ref('')

// ===== 表单字段 =====
const username = ref('')    // 用户名不可改，只读展示
const avatar = ref('')      // 已保存的头像 URL（后端返回的）
const nickname = ref('')
const phone = ref('')
const email = ref('')
const gender = ref(0)

// ===== 头像：选图 / 本地预览 =====
const avatarFile = ref<File | null>(null)   // 本次新选的文件，点「保存」时才上传
const previewUrl = ref('')                  // 本地预览地址（objectURL）
const fileInput = ref<HTMLInputElement | null>(null)

/**
 * 实际显示哪张图：优先本地预览（用户刚选的），否则用后端的 avatar。
 * 这样"选完立刻能看到新头像"，不用等上传完成，也不受网络影响。
 */
const avatarSrc = computed(() => previewUrl.value || avatar.value)

const GENDERS = [
  { value: 0, label: '保密' },
  { value: 1, label: '男' },
  { value: 2, label: '女' },
]

// 打开弹窗即拉取最新资料回填（不直接用顶部的缓存，避免信息过期）
onMounted(async () => {
  try {
    const p = await getProfile()
    username.value = p.username ?? ''
    avatar.value = p.avatar ?? ''
    nickname.value = p.nickname ?? ''
    phone.value = p.phone ?? ''
    email.value = p.email ?? ''
    gender.value = p.gender ?? 0
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})

/** 唤起系统文件选择框（隐藏的 input 用 ref 主动 click()，比 label 更可控） */
function chooseAvatar() {
  if (saving.value) return
  fileInput.value?.click()
}

/** 释放 objectURL：不释放的话那块 blob 内存会一直占着，直到页面卸载 */
function releasePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

/** 选中文件 → 前端校验 → 生成本地预览（此刻还没上传） */
function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // ⭐ 立刻清空 value：已经取出的 File 对象不受影响，
  //   但这样"再选同一张图"仍然会触发 change（否则第二次选它毫无反应）
  input.value = ''
  if (!file) return

  if (!ALLOWED_TYPES.includes(file.type)) {
    errorMsg.value = '仅支持 jpg / png / gif / webp / bmp 格式'
    return
  }
  if (file.size > MAX_SIZE) {
    errorMsg.value = '图片大小不能超过 5MB'
    return
  }

  errorMsg.value = ''
  releasePreview()                       // 先释放上一张的预览，再建新的
  avatarFile.value = file
  previewUrl.value = URL.createObjectURL(file)
}

// 弹窗销毁时释放预览，避免内存泄漏
onBeforeUnmount(releasePreview)

async function save() {
  // 前端先做一轮与后端一致的格式校验，避免白跑一次网络
  const p = phone.value.trim()
  const m = email.value.trim()
  if (p && !/^1[3-9]\d{9}$/.test(p)) {
    errorMsg.value = '手机号格式不正确'
    return
  }
  if (m && !/^\S+@\S+\.\S+$/.test(m)) {
    errorMsg.value = '邮箱格式不正确'
    return
  }

  saving.value = true
  errorMsg.value = ''
  try {
    // 1) 有新选的头像 → 先传到 OSS，换回一个可访问的 URL
    let avatarUrl = avatar.value
    if (avatarFile.value) {
      avatarUrl = await uploadImage(avatarFile.value)
    }

    // 2) 再提交资料（字段均可选，不传 = 后端不更新）
    const updated = await updateProfile({
      // 空字符串转 undefined：不传 = 后端不更新该字段
      nickname: nickname.value.trim() || undefined,
      avatar: avatarUrl || undefined,
      phone: p || undefined,
      email: m || undefined,
      gender: gender.value,
    })
    emit('saved', updated)
    emit('close')
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="mask" @click.self="emit('close')">
    <div class="dialog">
      <div class="head">
        <span class="title">修改个人信息</span>
        <button class="close" @click="emit('close')">×</button>
      </div>

      <div class="body">
        <p v-if="loading" class="tip">加载中…</p>

        <template v-else>
          <!-- 头像：显示 + 更换（选完立即本地预览，点「保存」才上传） -->
          <div class="avatar-row">
            <el-avatar :size="64" :src="avatarSrc" class="avatar">
              {{ (nickname || username).charAt(0).toUpperCase() }}
            </el-avatar>
            <div class="avatar-side">
              <button class="avatar-btn" :disabled="saving" @click="chooseAvatar">
                更换头像
              </button>
              <span class="avatar-hint">
                {{
                  avatarFile
                    ? '已选择新头像，点「保存」后生效'
                    : '支持 jpg / png / gif / webp / bmp，不超过 5MB'
                }}
              </span>
            </div>
            <!-- 隐藏的文件选择框：靠 ref 主动 click() 唤起 -->
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              class="file-input"
              @change="onFileChange"
            />
          </div>

          <div class="row">
            <label class="label">用户名</label>
            <input class="field readonly" :value="username" disabled />
          </div>

          <div class="row">
            <label class="label">昵称</label>
            <input v-model="nickname" class="field" placeholder="请输入昵称" />
          </div>

          <div class="row">
            <label class="label">手机号</label>
            <input v-model="phone" class="field" placeholder="11 位手机号" />
          </div>

          <div class="row">
            <label class="label">邮箱</label>
            <input v-model="email" class="field" placeholder="example@mail.com" />
          </div>

          <div class="row">
            <label class="label">性别</label>
            <div class="genders">
              <span
                v-for="g in GENDERS"
                :key="g.value"
                class="gender"
                :class="{ active: gender === g.value }"
                @click="gender = g.value"
              >
                {{ g.label }}
              </span>
            </div>
          </div>

          <p v-if="errorMsg" class="error">{{ errorMsg }}</p>

          <button class="submit" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </template>
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
  z-index: 110;               /* 高于"我的"下拉菜单(30)与轻提示(99) */
}
.dialog {
  width: 92%;
  max-width: 400px;
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
.body {
  padding: 16px;
}
.tip {
  margin: 0;
  padding: 20px 0;
  text-align: center;
  color: #999;
  font-size: 13px;
}

/* ===== 头像区 ===== */
.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}
.avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #ff6057, #e1251b);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
}
.avatar-side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.avatar-btn {
  align-self: flex-start;
  padding: 6px 14px;
  font-size: 13px;
  color: #e1251b;
  background: #fff;
  border: 1px solid #e1251b;
  border-radius: 6px;
  cursor: pointer;
}
.avatar-btn:hover {
  background: #fff5f4;
}
.avatar-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.avatar-hint {
  font-size: 12px;
  color: #aaa;
  line-height: 1.4;
}
/* 隐藏原生文件选择框（仍可被 click() 唤起） */
.file-input {
  display: none;
}

.row {
  margin-bottom: 14px;
}
.label {
  display: block;
  font-size: 13px;
  color: #999;
  margin-bottom: 6px;
}
.field {
  width: 100%;
  box-sizing: border-box;
  height: 40px;
  padding: 0 12px;
  font-size: 14px;
  border: 1px solid #ddd;
  border-radius: 6px;
  outline: none;
}
.field:focus {
  border-color: #e1251b;
}
.field.readonly {
  background: #f7f7f7;
  color: #999;
  cursor: not-allowed;
}

.genders {
  display: flex;
  gap: 8px;
}
.gender {
  flex: 1;
  text-align: center;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 8px 0;
  font-size: 13px;
  color: #333;
  cursor: pointer;
}
.gender:hover {
  border-color: #e1251b;
}
.gender.active {
  border-color: #e1251b;
  color: #e1251b;
  background: #fff5f4;
}

.error {
  margin: 0 0 10px;
  font-size: 12px;
  color: #c0392b;
}

.submit {
  width: 100%;
  height: 42px;
  color: #fff;
  background: #e1251b;
  border: none;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}
.submit:hover {
  background: #c91f17;
}
.submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
