<script setup lang="ts">
/**
 * 登录 / 注册 / 忘记密码 —— 同一个页面三种面板切换（mode 状态驱动，不跳路由）。
 *
 * 三个入口的关系：
 *   - 被详情页"请先登录"拦下来 → 进本页并提示"请先登录"（1 秒后消失）
 *   - 登录  → POST /auth/login     → 拿 token + userId 存本地
 *   - 注册  → POST /auth/register  → 后端"注册即登录"，直接返回 token
 *   - 忘记密码 → PUT /user/password/forget → 凭用户名+注册手机号重置，成功后回登录面板
 *
 * 登录/注册成功后都会 router.replace 回 redirect 指向的页面（没有就回首页）。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register, forgetPassword } from '@/api/auth'
import { setAuth } from '@/utils/auth'

type Mode = 'login' | 'register' | 'forget'

const route = useRoute()
const router = useRouter()

/** 当前面板 */
const mode = ref<Mode>('login')

// ===== "请先登录"轻提示：进入本页时弹出，存在 1 秒后自动消失 =====
const tip = ref('')
let tipTimer: ReturnType<typeof setTimeout> | undefined

onMounted(() => {
  showTip('请先登录', 1000)
})
// 离开页面时清掉定时器，避免在已卸载的组件上改状态
onBeforeUnmount(() => clearTimeout(tipTimer))

/** 显示轻提示，ms 毫秒后自动消失 */
function showTip(msg: string, ms: number) {
  tip.value = msg
  clearTimeout(tipTimer)
  tipTimer = setTimeout(() => (tip.value = ''), ms)
}

// ===== 表单字段：三个面板共用这几个槽位，按面板取用 =====
const username = ref('')
const password = ref('')
const nickname = ref('')
const phone = ref('')
const email = ref('')
const newPassword = ref('')

const loading = ref(false)
const errorMsg = ref('')

const title = computed(() => ({ login: '登录', register: '注册', forget: '重置密码' })[mode.value])
const submitText = computed(
  () => ({ login: '登录', register: '注册并登录', forget: '重置密码' })[mode.value],
)

/** 切换面板：顺手清掉上一个面板留下的错误提示 */
function switchMode(next: Mode) {
  mode.value = next
  errorMsg.value = ''
}

/** 回到被拦下来的页面（没有 redirect 就回首页） */
function goBack() {
  const redirect = route.query.redirect
  router.replace(typeof redirect === 'string' && redirect ? redirect : '/')
}

/** 登录 */
async function doLogin() {
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await login(username.value.trim(), password.value)
    // 存登录态：之后所有请求由 api/request.ts 的请求拦截器自动带上 token
    setAuth(res.token, { userId: res.userId, username: res.username })
    goBack()
  } catch (e) {
    // 后端会把"用户名或密码错误，剩余 N 次机会"放在 message 里，直接展示
    errorMsg.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}

/** 注册：后端"注册即登录"，返回的 token 直接可用 */
async function doRegister() {
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await register({
      username: username.value.trim(),
      password: password.value,
      // 选填项为空时不传（避免给后端塞空字符串）
      nickname: nickname.value.trim() || undefined,
      phone: phone.value.trim() || undefined,
      email: email.value.trim() || undefined,
      gender: 0,
    })
    setAuth(res.token, { userId: res.id, username: res.username })
    goBack()
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '注册失败'
  } finally {
    loading.value = false
  }
}

/** 忘记密码：前端先做一遍与后端相同的校验，减少一次无用请求 */
async function doForget() {
  if (!username.value.trim() || !phone.value.trim() || !newPassword.value) {
    errorMsg.value = '请填写用户名、手机号和新密码'
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone.value.trim())) {
    errorMsg.value = '手机号格式不正确'
    return
  }
  if (newPassword.value.length < 6 || newPassword.value.length > 32) {
    errorMsg.value = '新密码长度须为 6~32 位'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await forgetPassword({
      username: username.value.trim(),
      phone: phone.value.trim(),
      newPassword: newPassword.value,
    })
    // 重置成功 → 回登录面板，让用户用新密码登录
    password.value = ''
    newPassword.value = ''
    switchMode('login')
    showTip('密码已重置，请用新密码登录', 2000)
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '重置失败'
  } finally {
    loading.value = false
  }
}

/** 统一的提交入口：按当前面板分发 */
function onSubmit() {
  if (mode.value === 'login') return doLogin()
  if (mode.value === 'register') return doRegister()
  return doForget()
}
</script>

<template>
  <div class="login-page">
    <!-- 轻提示：进入页面时的"请先登录"，或重置密码成功提示 -->
    <Transition name="tip">
      <div v-if="tip" class="tip-bubble">{{ tip }}</div>
    </Transition>

    <div class="login-card">
      <h2 class="title">{{ title }}</h2>
      <p class="sub">登录后即可加入购物车、下单购买</p>

      <!-- ===== 登录 / 注册：用户名 + 密码 ===== -->
      <template v-if="mode !== 'forget'">
        <input
          v-model="username"
          class="field"
          type="text"
          placeholder="用户名"
          @keyup.enter="onSubmit"
        />
        <input
          v-model="password"
          class="field"
          type="password"
          :placeholder="mode === 'register' ? '设置密码' : '密码'"
          @keyup.enter="onSubmit"
        />
      </template>

      <!-- ===== 注册补充信息（选填） ===== -->
      <template v-if="mode === 'register'">
        <input
          v-model="nickname"
          class="field"
          type="text"
          placeholder="昵称（选填）"
          @keyup.enter="onSubmit"
        />
        <input
          v-model="phone"
          class="field"
          type="text"
          placeholder="手机号（建议填写，找回密码要用）"
          @keyup.enter="onSubmit"
        />
        <input
          v-model="email"
          class="field"
          type="text"
          placeholder="邮箱（选填）"
          @keyup.enter="onSubmit"
        />
      </template>

      <!-- ===== 忘记密码：用户名 + 注册手机号 + 新密码 ===== -->
      <template v-else-if="mode === 'forget'">
        <input
          v-model="username"
          class="field"
          type="text"
          placeholder="用户名"
          @keyup.enter="onSubmit"
        />
        <input
          v-model="phone"
          class="field"
          type="text"
          placeholder="注册时填写的手机号"
          @keyup.enter="onSubmit"
        />
        <input
          v-model="newPassword"
          class="field"
          type="password"
          placeholder="新密码（6~32 位）"
          @keyup.enter="onSubmit"
        />
      </template>

      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>

      <button class="submit" :disabled="loading" @click="onSubmit">
        {{ loading ? '提交中...' : submitText }}
      </button>

      <!-- ===== 底部事件入口：忘记密码 / 注册 ===== -->
      <div class="links">
        <template v-if="mode === 'login'">
          <button class="link" @click="switchMode('forget')">忘记密码？</button>
          <button class="link" @click="switchMode('register')">还没有账号？去注册</button>
        </template>
        <template v-else>
          <button class="link" @click="switchMode('login')">已有账号？去登录</button>
          <button v-if="mode === 'register'" class="link" @click="switchMode('forget')">
            忘记密码？
          </button>
        </template>
      </div>

      <button class="back" @click="router.back()">返回上一页</button>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  padding: 80px 20px;
}

/* 轻提示气泡：固定在页面顶部居中，1 秒后淡出 */
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

.login-card {
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 12px;
  padding: 28px 24px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
}
.title {
  margin: 0 0 4px;
  font-size: 20px;
  text-align: center;
}
.sub {
  margin: 0 0 20px;
  font-size: 12px;
  color: #999;
  text-align: center;
}
.field {
  display: block;
  width: 100%;
  box-sizing: border-box;
  height: 42px;
  padding: 0 12px;
  margin-bottom: 12px;
  font-size: 14px;
  border: 1px solid #ddd;
  border-radius: 6px;
  outline: none;
}
.field:focus {
  border-color: #e1251b;
}
.error {
  margin: 0 0 12px;
  color: #c0392b;
  font-size: 13px;
}
.submit {
  width: 100%;
  height: 44px;
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

/* 底部两个事件入口 */
.links {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
}
.link {
  background: none;
  border: none;
  padding: 0;
  color: #e1251b;
  font-size: 13px;
  cursor: pointer;
}
.link:hover {
  text-decoration: underline;
}

.back {
  width: 100%;
  margin-top: 14px;
  height: 36px;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 6px;
  color: #666;
  font-size: 13px;
  cursor: pointer;
}
.back:hover {
  border-color: #e1251b;
  color: #e1251b;
}
</style>
