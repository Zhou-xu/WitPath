<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/ui/AppButton.vue'
import AppField from '@/components/ui/AppField.vue'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const ui = useUiStore()

const form = ref({ username: '', password: '', captchaAnswer: '' })
/** 雪花 ID 字符串（19 位大整数超出 JS 安全整数范围，后端已按字符串返回） */
const loginInitId = ref<string | null>(null)
const captchaImage = ref('')
const submitting = ref(false)
const error = ref('')
const loadingCaptcha = ref(false)

/** 创建登录会话并拉取验证码（后端三步式登录） */
async function refreshCaptcha() {
  loadingCaptcha.value = true
  try {
    loginInitId.value = await authApi.initSession()
    const captcha = await authApi.captcha(loginInitId.value)
    captchaImage.value = captcha.captchaImage
    form.value.captchaAnswer = ''
  } catch (e) {
    error.value = (e as Error).message || '验证码加载失败，请检查后端服务'
  } finally {
    loadingCaptcha.value = false
  }
}

async function submit() {
  if (!form.value.username || !form.value.password) {
    error.value = '请输入用户名和密码'
    return
  }
  if (!form.value.captchaAnswer) {
    error.value = '请输入图形验证码'
    return
  }
  if (loginInitId.value == null) {
    await refreshCaptcha()
    return
  }

  submitting.value = true
  error.value = ''
  try {
    const data = await authApi.login({
      loginInitId: loginInitId.value,
      username: form.value.username,
      password: form.value.password,
      captchaAnswer: form.value.captchaAnswer,
    })
    user.applyLogin(data)
    await user.loadProfile(true)
    ui.toast(`欢迎回来，${data.nickname || data.username}`, 'success')
    const redirect = (route.query.redirect as string) || '/app/dashboard'
    router.replace(redirect)
  } catch (e) {
    error.value = (e as Error).message || '登录失败'
    await refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

function fillDemo() {
  form.value.username = 'demo'
  form.value.password = 'witpath123'
}

onMounted(refreshCaptcha)
</script>

<template>
  <div class="auth">
    <section class="auth__brand">
      <div class="auth__brand-inner">
        <span class="auth__logo">WitPath</span>
        <h2>学途知行</h2>
        <p class="auth__slogan">
          把每一次学习沉淀成可追溯的轨迹，<br />
          让计划、记录与 AI 建议形成闭环。
        </p>
        <ul class="auth__points">
          <li>目标拆解与进度自动回算</li>
          <li>学习记录多维统计与趋势洞察</li>
          <li>资料向量化后的语义问答</li>
        </ul>
      </div>
    </section>

    <section class="auth__panel">
      <div class="auth__card card">
        <header class="stack" style="gap: 6px">
          <h1>登录</h1>
          <span class="text-sm text-muted">使用账号密码进入学习工作台</span>
        </header>

        <form class="auth__form" @submit.prevent="submit">
          <AppField label="用户名" required>
            <input
              v-model="form.username"
              class="control"
              autocomplete="username"
              placeholder="请输入用户名"
            />
          </AppField>

          <AppField label="密码" required>
            <input
              v-model="form.password"
              class="control"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </AppField>

          <AppField label="图形验证码" required :error="error">
            <div class="auth__captcha">
              <input v-model="form.captchaAnswer" class="control" placeholder="请输入右侧验证码" />
              <button
                type="button"
                class="auth__captcha-img"
                :title="loadingCaptcha ? '加载中' : '点击刷新验证码'"
                @click="refreshCaptcha()"
              >
                <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
                <span v-else class="text-xs text-muted">加载中…</span>
              </button>
            </div>
          </AppField>

          <AppButton type="submit" variant="primary" block :loading="submitting">登录</AppButton>
        </form>

        
        <div class="auth__foot">
          <!-- 测试开发用户，测试功能是否正常 -->
          <!-- <button class="auth__link" type="button" @click="fillDemo">填入演示账号</button> -->
          <RouterLink class="auth__link" to="/register">还没有账号？去注册</RouterLink>
        </div>

        <RouterLink class="auth__back text-xs text-muted" to="/">← 返回首页</RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  min-height: 100vh;
  background: var(--c-bg);
}

.auth__brand {
  position: relative;
  display: grid;
  place-items: center;
  padding: var(--sp-12);
  background:
    radial-gradient(120% 90% at 20% 10%, rgba(185, 113, 44, 0.16), transparent 60%),
    linear-gradient(160deg, #fbf9f6, #f2ece2);
  border-right: 1px solid var(--c-border);
}

.auth__brand-inner {
  max-width: 380px;
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.auth__logo {
  font-family: var(--font-brand);
  /* 草书字形视觉尺寸偏小，字号需比普通字体大一档才能对齐观感 */
  font-size: 46px;
  color: var(--c-accent);
  line-height: 0.9;
}

.auth__brand h2 {
  font-size: 26px;
}

.auth__slogan {
  color: var(--c-text-secondary);
  line-height: 1.8;
}

.auth__points {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: var(--sp-2);
  color: var(--c-text-secondary);
  font-size: 13px;
}

.auth__points li::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 8px;
  border-radius: 50%;
  background: var(--c-accent);
  vertical-align: middle;
}

.auth__panel {
  display: grid;
  place-items: center;
  padding: var(--sp-8);
}

.auth__card {
  width: 100%;
  max-width: 380px;
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
  padding: var(--sp-8);
}

.auth__form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.auth__captcha {
  display: grid;
  grid-template-columns: 1fr 116px;
  gap: 10px;
}

.auth__captcha-img {
  display: grid;
  place-items: center;
  height: 38px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-control);
  background: var(--c-surface-muted);
  overflow: hidden;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease);
}

.auth__captcha-img:hover {
  border-color: var(--c-accent-ring);
}

.auth__captcha-img img {
  width: 100%;
  height: 100%;
  /* 后端按 232x76(=2x 展示框比例)生成，contain 可铺满且不裁切字符 */
  object-fit: contain;
}

.auth__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.auth__link {
  color: var(--c-accent-hover);
  font-size: 13px;
}

.auth__link:hover {
  text-decoration: underline;
}

.auth__back {
  text-align: center;
}

@media (max-width: 900px) {
  .auth {
    grid-template-columns: 1fr;
  }
  .auth__brand {
    display: none;
  }
}
</style>
