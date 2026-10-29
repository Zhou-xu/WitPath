<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppField from '@/components/ui/AppField.vue'
import AppModal from '@/components/ui/AppModal.vue'
import { authApi } from '@/api'
import { useUiStore } from '@/stores/ui'

/** 修改密码弹窗：就地校验 + 后端错误回显，与「修改头像」保持一致的交互 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const ui = useUiStore()

const MIN = 6

const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const showText = ref(false)
const submitting = ref(false)
const serverError = ref('')

const inputType = computed(() => (showText.value ? 'text' : 'password'))

/** 就地校验：只在用户已输入时提示，避免一打开就红一片 */
const errors = computed(() => {
  const map: Record<string, string> = {}
  if (form.newPassword && form.newPassword.length < MIN) {
    map.newPassword = `新密码至少 ${MIN} 位`
  } else if (form.newPassword && form.oldPassword && form.newPassword === form.oldPassword) {
    map.newPassword = '新密码不能与当前密码相同'
  }
  if (form.confirm && form.confirm !== form.newPassword) {
    map.confirm = '两次输入的新密码不一致'
  }
  return map
})

const canSubmit = computed(
  () =>
    !submitting.value &&
    Boolean(form.oldPassword && form.newPassword && form.confirm) &&
    form.newPassword.length >= MIN &&
    form.newPassword === form.confirm &&
    form.newPassword !== form.oldPassword,
)

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  serverError.value = ''
  try {
    await authApi.changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ui.toast('密码已修改，请牢记新密码', 'success')
    emit('close')
  } catch (e) {
    // 后端已区分「原密码不正确」「新密码不能与原密码相同」，直接回显到当前密码字段
    serverError.value = (e as Error).message || '修改失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function reset() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirm = ''
  showText.value = false
  serverError.value = ''
  submitting.value = false
}

/** 打开时保证是干净表单；关闭后清空，避免密码残留在内存 */
watch(() => props.open, reset)
</script>

<template>
  <AppModal :open="open" title="修改密码" :width="460" @close="emit('close')">
    <form class="pcm" @submit.prevent="submit">
      <AppField label="当前密码" required :error="serverError">
        <input
          v-model="form.oldPassword"
          :type="inputType"
          class="control"
          autocomplete="current-password"
          placeholder="请输入当前密码"
        />
      </AppField>
      <AppField label="新密码" required :error="errors.newPassword" hint="至少 6 位，建议字母与数字组合">
        <input
          v-model="form.newPassword"
          :type="inputType"
          class="control"
          autocomplete="new-password"
          placeholder="请输入新密码"
        />
      </AppField>
      <AppField label="确认新密码" required :error="errors.confirm">
        <input
          v-model="form.confirm"
          :type="inputType"
          class="control"
          autocomplete="new-password"
          placeholder="再次输入新密码"
        />
      </AppField>

      <label class="pcm__show">
        <input v-model="showText" type="checkbox" />
        <span class="text-sm">显示密码</span>
      </label>
    </form>

    <template #footer>
      <AppButton @click="emit('close')">取消</AppButton>
      <AppButton variant="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
        确认修改
      </AppButton>
    </template>
  </AppModal>
</template>

<style scoped>
.pcm {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.pcm__show {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  color: var(--c-text-secondary);
  cursor: pointer;
}

.pcm__show input {
  width: 14px;
  height: 14px;
  accent-color: var(--c-accent);
}
</style>
