<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppButton from '@/components/ui/AppButton.vue'
import AppField from '@/components/ui/AppField.vue'
import AppSelect, { type AppSelectOption } from '@/components/ui/AppSelect.vue'
import { authApi } from '@/api'
import { fetchPublicIp } from '@/utils/publicIp'
import { useUiStore } from '@/stores/ui'

const router = useRouter()
const ui = useUiStore()

const studyStageOptions: AppSelectOption[] = [
  { label: '高中', value: '高中' },
  { label: '本科', value: '本科' },
  { label: '考研', value: '考研' },
  { label: '研究生', value: '研究生' },
  { label: '职业提升', value: '职业提升' },
]

const form = ref({
  username: '',
  password: '',
  confirm: '',
  nickname: '',
  email: '',
  studyStage: '',
  studyGoal: '',
  preferredTags: '',
  captchaAnswer: '',
})

const loginInitId = ref<string | null>(null)
const captchaImage = ref('')
const submitting = ref(false)
const loadingCaptcha = ref(false)
const error = ref('')

// 注册时随请求带给后端，用于异步记录用户归属地；获取失败留空，后端从请求头兜底
const publicIp = ref('')

async function refreshCaptcha() {
  loadingCaptcha.value = true
  try {
    loginInitId.value = await authApi.initSession()
    const captcha = await authApi.captcha(loginInitId.value)
    captchaImage.value = captcha.captchaImage
    form.value.captchaAnswer = ''
  } catch (e) {
    error.value = (e as Error).message || '验证码加载失败'
  } finally {
    loadingCaptcha.value = false
  }
}

async function submit() {
  error.value = ''
  if (form.value.username.length < 3) {
    error.value = '用户名至少 3 位，仅支持字母、数字与下划线'
    return
  }
  if (form.value.password.length < 6) {
    error.value = '密码至少 6 位'
    return
  }
  if (form.value.password !== form.value.confirm) {
    error.value = '两次输入的密码不一致'
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
  try {
    await authApi.register({
      loginInitId: loginInitId.value,
      username: form.value.username,
      password: form.value.password,
      nickname: form.value.nickname || form.value.username,
      email: form.value.email,
      studyStage: form.value.studyStage,
      studyGoal: form.value.studyGoal,
      preferredTags: form.value.preferredTags,
      captchaAnswer: form.value.captchaAnswer,
      clientIp: publicIp.value || undefined,
    })
    ui.toast('注册成功，请登录', 'success')
    router.push('/login')
  } catch (e) {
    error.value = (e as Error).message || '注册失败'
    await refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
  // 探测公网 IP，失败静默（fetchPublicIp 内部已兜底，不会 reject）
  fetchPublicIp().then((ip) => (publicIp.value = ip))
})
</script>

<template>
  <div class="auth">
    <div class="auth__card card">
      <header class="stack" style="gap: 6px">
        <h1>注册账号</h1>
        <span class="text-sm text-muted">填写基础信息，开启你的学习轨迹</span>
      </header>

      <form class="auth__form" @submit.prevent="submit">
        <div class="auth__grid">
          <AppField label="用户名" required hint="3~30 位字母、数字或下划线">
            <input v-model="form.username" class="control" placeholder="如 witpath_demo" />
          </AppField>
          <AppField label="昵称">
            <input v-model="form.nickname" class="control" placeholder="选填，默认与用户名相同" />
          </AppField>
          <AppField label="密码" required>
            <input v-model="form.password" class="control" type="password" placeholder="至少 6 位" />
          </AppField>
          <AppField label="确认密码" required>
            <input v-model="form.confirm" class="control" type="password" placeholder="再次输入密码" />
          </AppField>
          <AppField label="邮箱">
            <input v-model="form.email" class="control" placeholder="选填" />
          </AppField>
          <AppField label="学习阶段">
            <AppSelect v-model="form.studyStage" :options="studyStageOptions" placeholder="请选择" />
          </AppField>
        </div>

        <AppField label="当前学习目标">
          <textarea
            v-model="form.studyGoal"
            class="control"
            rows="2"
            placeholder="例如：三个月内完成数据结构与算法系统复习"
          />
        </AppField>

        <AppField label="偏好标签" hint="逗号分隔，用于 AI 建议的个性化">
          <input v-model="form.preferredTags" class="control" placeholder="算法,英语,专业课" />
        </AppField>

        <AppField label="图形验证码" required :error="error">
          <div class="auth__captcha">
            <input v-model="form.captchaAnswer" class="control" placeholder="请输入右侧验证码" />
            <button type="button" class="auth__captcha-img" title="点击刷新" @click="refreshCaptcha()">
              <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
              <span v-else class="text-xs text-muted">加载中…</span>
            </button>
          </div>
        </AppField>

        <AppButton type="submit" variant="primary" block :loading="submitting">注册</AppButton>
      </form>

      <div class="auth__foot">
        <RouterLink class="auth__link" to="/login">已有账号？去登录</RouterLink>
        <RouterLink class="auth__link text-xs text-muted" to="/">返回首页</RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: var(--sp-8) var(--sp-4);
  background:
    radial-gradient(120% 90% at 15% 5%, rgba(185, 113, 44, 0.14), transparent 60%),
    var(--c-bg);
}

.auth__card {
  width: 100%;
  max-width: 560px;
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

.auth__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
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
}

.auth__link {
  color: var(--c-accent-hover);
  font-size: 13px;
}

.auth__link:hover {
  text-decoration: underline;
}

@media (max-width: 640px) {
  .auth__grid {
    grid-template-columns: 1fr;
  }
}
</style>
