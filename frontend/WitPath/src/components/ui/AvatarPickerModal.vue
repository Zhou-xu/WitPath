<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import UserAvatar from '@/components/ui/UserAvatar.vue'
import AppModal from '@/components/ui/AppModal.vue'
import { avatarApi, type AvatarPresetVO, type UserVO } from '@/api'
import { useUiStore } from '@/stores/ui'
import { useUserStore } from '@/stores/user'

/** 修改头像弹窗：在线头像（内置 SVG 预设）/ 本地上传，均写入阿里云 OSS */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const ui = useUiStore()
const user = useUserStore()

type TabKey = 'preset' | 'local'
const tabs: { key: TabKey; label: string }[] = [
  { key: 'preset', label: '在线头像' },
  { key: 'local', label: '本地头像' },
]

const tab = ref<TabKey>('preset')

const presets = ref<AvatarPresetVO[]>([])
const loadingPresets = ref(false)
const selectedPreset = ref('')

const file = ref<File | null>(null)
const localPreview = ref('')
const submitting = ref(false)

const MAX_SIZE = 5 * 1024 * 1024
const ALLOWED = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp']

const dataUri = (svg: string) => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`

async function loadPresets() {
  if (presets.value.length || loadingPresets.value) return
  loadingPresets.value = true
  try {
    presets.value = await avatarApi.presets()
  } catch (e) {
    ui.toast((e as Error).message || '在线头像加载失败', 'error')
  } finally {
    loadingPresets.value = false
  }
}

function pick(fileList: FileList | null) {
  const picked = fileList?.[0]
  if (!picked) return
  const ext = picked.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED.includes(ext)) {
    ui.toast('仅支持 jpg / png / webp / gif / bmp 图片', 'warning')
    return
  }
  if (picked.size > MAX_SIZE) {
    ui.toast('图片不能超过 5MB', 'warning')
    return
  }
  releasePreview()
  file.value = picked
  localPreview.value = URL.createObjectURL(picked)
}

function releasePreview() {
  if (localPreview.value) {
    URL.revokeObjectURL(localPreview.value)
    localPreview.value = ''
  }
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  pick(event.dataTransfer?.files ?? null)
}

function finish(updated: UserVO) {
  user.profile = updated
  ui.toast('头像已更新', 'success')
  emit('close')
}

async function usePreset() {
  if (!selectedPreset.value || submitting.value) return
  submitting.value = true
  try {
    finish(await avatarApi.applyPreset(selectedPreset.value))
  } catch (e) {
    ui.toast((e as Error).message || '设置失败', 'error')
  } finally {
    submitting.value = false
  }
}

async function useLocal() {
  if (!file.value || submitting.value) return
  submitting.value = true
  try {
    finish(await avatarApi.upload(file.value))
  } catch (e) {
    ui.toast((e as Error).message || '上传失败', 'error')
  } finally {
    submitting.value = false
  }
}

const canSubmit = computed(() =>
  tab.value === 'preset' ? Boolean(selectedPreset.value) : Boolean(file.value),
)

watch(
  () => props.open,
  (val) => {
    if (val) {
      loadPresets()
    } else {
      // 关闭后重置本地选择，避免下次打开残留旧文件
      file.value = null
      releasePreview()
      selectedPreset.value = ''
      submitting.value = false
    }
  },
)

onBeforeUnmount(releasePreview)
</script>

<template>
  <AppModal :open="open" title="修改头像" :width="520" @close="emit('close')">
    <div class="apm">
      <div class="apm__current">
        <UserAvatar :url="user.profile?.avatarUrl" :name="user.displayName" :size="56" />
        <div class="stack" style="gap: 2px">
          <strong>{{ user.displayName }}</strong>
          <span class="text-xs text-muted">选择在线头像或上传本地图片，将保存到阿里云 OSS</span>
        </div>
      </div>

      <div class="apm__tabs" role="tablist">
        <button
          v-for="item in tabs"
          :key="item.key"
          class="apm__tab"
          :class="{ 'is-active': tab === item.key }"
          role="tab"
          :aria-selected="tab === item.key"
          @click="tab = item.key"
        >
          {{ item.label }}
        </button>
      </div>

      <div v-if="tab === 'preset'" class="apm__panel">
        <div v-if="loadingPresets" class="apm__loading">
          <AppSkeleton :lines="3" />
        </div>
        <ul v-else class="apm__grid">
          <li v-for="item in presets" :key="item.id">
            <button
              class="apm__item"
              :class="{ 'is-active': selectedPreset === item.id }"
              :title="item.name"
              @click="selectedPreset = item.id"
            >
              <img :src="dataUri(item.svg)" :alt="item.name" />
            </button>
          </li>
        </ul>
      </div>

      <div v-else class="apm__panel">
        <div
          class="apm__drop"
          @click="($refs.input as HTMLInputElement | undefined)?.click()"
          @dragover.prevent
          @drop="onDrop"
        >
          <template v-if="localPreview">
            <UserAvatar :url="localPreview" :size="72" />
            <span class="text-sm">{{ file?.name }}</span>
            <span class="text-xs text-muted">点击可重新选择</span>
          </template>
          <template v-else>
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
              <path
                d="M12 16V6m0 0L8 10m4-4l4 4M5 17v2a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-2"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <span class="text-sm">点击选择图片，或拖拽到此处</span>
            <span class="text-xs text-muted">支持 jpg / png / webp / gif / bmp，不超过 5MB</span>
          </template>
        </div>
        <input
          ref="input"
          class="apm__file"
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif,image/bmp"
          @change="pick(($event.target as HTMLInputElement).files)"
        />
      </div>
    </div>

    <template #footer>
      <AppButton @click="emit('close')">取消</AppButton>
      <AppButton
        variant="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="tab === 'preset' ? usePreset() : useLocal()"
      >
        {{ tab === 'preset' ? '使用所选头像' : '上传并使用' }}
      </AppButton>
    </template>
  </AppModal>
</template>

<style scoped>
.apm {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.apm__current {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 12px;
  border-radius: var(--r-control);
  background: var(--c-bg-subtle);
}

.apm__tabs {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border-radius: var(--r-control);
  background: var(--c-surface-muted);
  align-self: flex-start;
}

.apm__tab {
  padding: 6px 14px;
  border-radius: calc(var(--r-control) - 2px);
  font-size: 13px;
  color: var(--c-text-secondary);
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.apm__tab.is-active {
  background: var(--c-surface);
  color: var(--c-accent-hover);
  font-weight: 500;
  box-shadow: var(--shadow-xs);
}

.apm__panel {
  min-height: 188px;
}

.apm__loading {
  padding: var(--sp-2) 0;
}

.apm__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.apm__item {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid transparent;
  transition: transform var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease);
}

.apm__item img {
  width: 100%;
  height: 100%;
  display: block;
}

.apm__item:hover {
  transform: translateY(-2px);
}

.apm__item.is-active {
  border-color: var(--c-accent);
  box-shadow: 0 0 0 3px var(--c-accent-soft);
}

.apm__drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 188px;
  padding: var(--sp-4);
  border: 1px dashed var(--c-border-strong);
  border-radius: var(--r-card);
  background: var(--c-bg-subtle);
  color: var(--c-text-secondary);
  text-align: center;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease);
}

.apm__drop:hover {
  border-color: var(--c-accent);
  background: var(--c-accent-soft);
}

.apm__file {
  display: none;
}

@media (max-width: 520px) {
  .apm__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .apm__item,
  .apm__tab {
    transition: none;
  }

  .apm__item:hover {
    transform: none;
  }
}
</style>
