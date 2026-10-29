<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppField from '@/components/ui/AppField.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppSelect, { type AppSelectOption } from '@/components/ui/AppSelect.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import { aiConfigApi, type ModelOptionVO, type UserAiConfigVO } from '@/api'
import { useUiStore } from '@/stores/ui'

/**
 * 个人大模型配置弹窗
 * <p>
 * 交互顺序：填地址与 Key → 点「获取模型」拉取列表 → 选择模型 → 保存。
 * 未选模型不允许提交，避免保存出一个跑不通的配置。
 * API Key 只在内存与请求体中出现，保存后后端加密落库、读取只返回掩码。
 * </p>
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved', config: UserAiConfigVO): void }>()

const ui = useUiStore()

const loading = ref(false)
const config = ref<UserAiConfigVO | null>(null)

const form = ref({
  provider: 'openai-compatible',
  baseUrl: '',
  apiKey: '',
  model: '',
  // 高级配置：向量模型（留空表示不覆盖系统默认）
  embeddingProvider: '',
  embeddingModel: '',
  embeddingApiKey: '',
  embeddingBaseUrl: '',
})

const showKey = ref(false)
const showAdvanced = ref(false)
const models = ref<ModelOptionVO[]>([])
const fetching = ref(false)
const saving = ref(false)
const removing = ref(false)
const switching = ref(false)

/** 当前生效来源：未配置个人模型、或已切回系统默认，都算系统默认 */
const usingDefault = computed(() => !config.value?.configured || config.value.useDefaultApi)

const modelOptions = computed<AppSelectOption[]>(() => [
  // 已保存的模型始终可见，避免用户只改地址时看不到原模型
  ...(config.value?.model && !models.value.some((m) => m.id === config.value?.model)
    ? [{ label: `${config.value.model}（当前）`, value: config.value.model }]
    : []),
  ...models.value.map((m) => ({ label: m.name === m.id ? m.id : `${m.name}（${m.id}）`, value: m.id })),
])

const canFetchModels = computed(() => Boolean(form.value.baseUrl.trim() && form.value.apiKey.trim()))

async function load() {
  loading.value = true
  try {
    config.value = await aiConfigApi.get()
    form.value.baseUrl = config.value.baseUrl ?? ''
    form.value.model = config.value.model ?? ''
    form.value.provider = config.value.provider ?? 'openai-compatible'
    form.value.embeddingProvider = config.value.embeddingProvider ?? ''
    form.value.embeddingModel = config.value.embeddingModel ?? ''
    form.value.embeddingBaseUrl = config.value.embeddingBaseUrl ?? ''
  } catch (e) {
    ui.toast((e as Error).message || '配置加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function fetchModels() {
  if (!form.value.baseUrl.trim() || !form.value.apiKey.trim()) {
    ui.toast('请先填写请求地址与 API Key', 'warning')
    return
  }
  fetching.value = true
  try {
    const list = await aiConfigApi.models({
      baseUrl: form.value.baseUrl.trim(),
      apiKey: form.value.apiKey.trim(),
    })
    models.value = list
    if (!list.length) {
      ui.toast('该地址未返回可用模型，请检查地址与 Key', 'warning')
    } else {
      ui.toast(`已获取 ${list.length} 个模型，请选择后保存`, 'success')
    }
  } catch (e) {
    ui.toast((e as Error).message || '获取模型失败', 'error')
  } finally {
    fetching.value = false
  }
}

async function save() {
  if (saving.value) return
  if (!form.value.model) {
    ui.toast('请先点击「获取模型」并选择要使用的模型', 'warning')
    return
  }
  saving.value = true
  try {
    const saved = await aiConfigApi.save({
      provider: form.value.provider,
      baseUrl: form.value.baseUrl.trim(),
      // 留空表示不修改已保存的 Key
      apiKey: form.value.apiKey.trim(),
      model: form.value.model,
      embeddingProvider: form.value.embeddingProvider.trim() || undefined,
      embeddingModel: form.value.embeddingModel.trim() || undefined,
      embeddingApiKey: form.value.embeddingApiKey.trim() || undefined,
      embeddingBaseUrl: form.value.embeddingBaseUrl.trim() || undefined,
    })
    config.value = saved
    ui.toast('模型配置已保存', 'success')
    emit('saved', saved)
    emit('close')
  } catch (e) {
    ui.toast((e as Error).message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

/** 切回系统默认：个人配置保留在库中，随时可改回 */
async function useDefault() {
  if (switching.value) return
  switching.value = true
  try {
    config.value = await aiConfigApi.useDefault()
    ui.toast('已切换为系统默认模型', 'success')
    emit('saved', config.value)
    emit('close')
  } catch (e) {
    ui.toast((e as Error).message || '切换失败', 'error')
  } finally {
    switching.value = false
  }
}

async function remove() {
  if (removing.value) return
  removing.value = true
  try {
    await aiConfigApi.remove()
    config.value = null
    models.value = []
    form.value.model = ''
    form.value.apiKey = ''
    form.value.embeddingModel = ''
    form.value.embeddingApiKey = ''
    ui.toast('已删除个人配置，恢复使用系统默认模型', 'success')
    emit('close')
  } catch (e) {
    ui.toast((e as Error).message || '删除失败', 'error')
  } finally {
    removing.value = false
  }
}

function reset() {
  showKey.value = false
  showAdvanced.value = false
  models.value = []
  form.value.apiKey = ''
  form.value.embeddingApiKey = ''
  config.value = null
}

watch(
  () => props.open,
  (val) => {
    if (val) {
      reset()
      load()
    }
  },
)
</script>

<template>
  <AppModal :open="open" title="模型配置" :width="560" @close="emit('close')">
    <AppSkeleton v-if="loading" :lines="4" />

    <div class="aic">
      <div v-if="config?.configured" class="aic__current">
        <div class="stack" style="gap: 2px">
          <strong>
            {{ usingDefault ? '当前：系统默认模型（个人配置已停用）' : `当前：${config.model}` }}
          </strong>
          <span v-if="!usingDefault" class="text-xs text-muted">
            {{ config.baseUrl }} · Key {{ config.apiKeyMasked }}
          </span>
          <span v-if="!usingDefault && config.embeddingOverridden" class="text-xs text-muted">
            向量模型：{{ config.embeddingModel }}
          </span>
          <span v-if="usingDefault" class="text-xs text-muted">
            保存下方配置后会自动启用个人模型
          </span>
        </div>
      </div>
      <div v-else class="aic__current">
        <div class="stack" style="gap: 2px">
          <strong>当前：系统默认模型</strong>
          <span class="text-xs text-muted">填写地址与 Key 后即可切换到个人模型</span>
        </div>
      </div>

      <!-- 大模型配置 -->
      <AppField label="请求地址" hint="OpenAI 兼容端点，如 https://ark.cn-beijing.volces.com/api/v3">
        <input v-model="form.baseUrl" class="control" placeholder="https://.../v1" />
      </AppField>
      <AppField
        label="API Key"
        :hint="config?.configured && config.apiKeyMasked ? `已保存 ${config.apiKeyMasked}，留空表示不修改` : undefined"
      >
        <input
          v-model="form.apiKey"
          class="control"
          :type="showKey ? 'text' : 'password'"
          autocomplete="off"
          :placeholder="config?.apiKeyMasked ? '留空则不修改已保存的 Key' : 'sk-...'"
        />
      </AppField>

      <div class="aic__row">
        <AppSelect
          v-model="form.model"
          :options="modelOptions"
          :disabled="modelOptions.length === 0"
          searchable
          search-placeholder="搜索模型名"
          :placeholder="modelOptions.length ? '请选择模型' : '先获取模型列表'"
        />
        <AppButton :loading="fetching" :disabled="!canFetchModels" @click="fetchModels">获取模型</AppButton>
      </div>
      <label class="aic__show">
        <input v-model="showKey" type="checkbox" />
        <span class="text-sm">显示 API Key</span>
      </label>

      <!-- 高级配置：向量模型 -->
      <button class="aic__toggle" type="button" @click="showAdvanced = !showAdvanced">
        <span>高级配置</span>
        <svg
          width="12"
          height="12"
          viewBox="0 0 12 12"
          :style="{ transform: showAdvanced ? 'rotate(180deg)' : 'none' }"
          aria-hidden="true"
        >
          <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <p class="text-xs text-muted">向量模型用于资料入库与知识检索；全部留空则使用系统默认配置。</p>

      <div v-if="showAdvanced" class="aic__advanced">
        <AppField label="向量模型服务商" hint="dashscope（阿里云百炼）/ ollama / 其它 OpenAI 兼容">
          <input v-model="form.embeddingProvider" class="control" placeholder="留空使用系统默认" />
        </AppField>
        <AppField label="向量模型名">
          <input v-model="form.embeddingModel" class="control" placeholder="如 qwen3.7-text-embedding" />
        </AppField>
        <AppField label="向量模型请求地址">
          <input v-model="form.embeddingBaseUrl" class="control" placeholder="留空使用系统默认" />
        </AppField>
        <AppField label="向量模型 API Key">
          <input v-model="form.embeddingApiKey" class="control" type="password" autocomplete="off" placeholder="留空表示不覆盖" />
        </AppField>
      </div>
    </div>

    <template #footer>
      <AppButton @click="emit('close')">取消</AppButton>
      <AppButton
        v-if="config?.configured && !usingDefault"
        :loading="switching"
        @click="useDefault"
      >
        使用默认 API
      </AppButton>
      <AppButton v-if="config?.configured" :loading="removing" @click="remove">删除配置</AppButton>
      <AppButton variant="primary" :loading="saving" @click="save">确认修改</AppButton>
    </template>
  </AppModal>
</template>

<style scoped>
.aic {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.aic__current {
  padding: 10px 12px;
  border-radius: var(--r-control);
  background: var(--c-bg-subtle);
}

.aic__row {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
}

.aic__row > :first-child {
  flex: 1;
  min-width: 0;
}

.aic__show {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  color: var(--c-text-secondary);
  cursor: pointer;
}

.aic__show input {
  width: 14px;
  height: 14px;
  accent-color: var(--c-accent);
}

.aic__toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  margin-top: var(--sp-1);
  font-size: 13px;
  color: var(--c-accent-hover);
}

.aic__toggle:hover {
  text-decoration: underline;
}

.aic__advanced {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-3);
  border: 1px dashed var(--c-border-strong);
  border-radius: var(--r-card);
  background: var(--c-bg-subtle);
}

@media (max-width: 520px) {
  .aic__row {
    flex-direction: column;
  }
}
</style>
