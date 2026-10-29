<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import { materialApi, type MaterialVO } from '@/api'
import { formatFileSize, relativeTime } from '@/utils/format'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const materials = ref<MaterialVO[]>([])
const loading = ref(true)
const uploading = ref(false)
const dragOver = ref(false)
const subject = ref('')
const ingestIds = ref<number[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

const stats = computed(() => ({
  total: materials.value.length,
  indexed: materials.value.filter((m) => m.vectorStatus === 2).length,
  chunks: materials.value.reduce((sum, m) => sum + (m.chunkCount ?? 0), 0),
}))

async function load() {
  loading.value = true
  try {
    const res = await materialApi.page({ page: 1, size: 50, subject: subject.value || undefined })
    materials.value = res.records
  } catch (e) {
    ui.toast((e as Error).message || '资料加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

async function uploadFiles(files: FileList | null) {
  if (!files || !files.length) return
  uploading.value = true
  let ok = 0
  for (const file of Array.from(files)) {
    try {
      await materialApi.upload(file, subject.value || undefined)
      ok += 1
    } catch (e) {
      ui.toast(`${file.name}：${(e as Error).message || '上传失败'}`, 'error')
    }
  }
  uploading.value = false
  if (ok) ui.toast(`已上传 ${ok} 个文件，正在解析入库`, 'success')
  await load()
}

function onDrop(event: DragEvent) {
  dragOver.value = false
  uploadFiles(event.dataTransfer?.files ?? null)
}

async function reIngest(item: MaterialVO) {
  ingestIds.value.push(item.id)
  try {
    await materialApi.ingest(item.id)
    ui.toast('已提交解析任务，稍后刷新查看结果', 'success')
    setTimeout(load, 2500)
  } catch (e) {
    ui.toast((e as Error).message || '解析任务提交失败', 'error')
  } finally {
    ingestIds.value = ingestIds.value.filter((id) => id !== item.id)
  }
}

async function remove(item: MaterialVO) {
  if (!confirm(`确认删除资料「${item.originalName}」？向量索引会同步移除。`)) return
  try {
    await materialApi.remove(item.id)
    ui.toast('资料已删除', 'success')
    await load()
  } catch (e) {
    ui.toast((e as Error).message || '删除失败', 'error')
  }
}

const parseTone = (status: number) => (status === 2 ? 'success' : status === 3 ? 'danger' : 'warning')
const vectorTone = (status: number) => (status === 2 ? 'success' : status === 3 ? 'danger' : 'info')

onMounted(load)
</script>

<template>
  <div class="mats">
    <section class="mats__head row-between">
      <div class="stack" style="gap: 4px">
        <h2>学习资料</h2>
        <span class="text-sm text-muted">
          共 {{ stats.total }} 份 · 已向量化 {{ stats.indexed }} 份 · {{ stats.chunks }} 个片段可供 AI 检索
        </span>
      </div>
      <div class="row" style="gap: 8px">
        <input v-model="subject" class="control mats__input" placeholder="按学科筛选" @keyup.enter="load" />
        <AppButton size="sm" @click="load">刷新</AppButton>
        <AppButton variant="primary" size="sm" :loading="uploading" @click="pickFile">上传资料</AppButton>
      </div>
    </section>

    <div
      class="mats__drop"
      :class="{ 'is-over': dragOver }"
      @dragover.prevent="dragOver = true"
      @dragleave.prevent="dragOver = false"
      @drop.prevent="onDrop"
      @click="pickFile"
    >
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M12 16V4m0 0L7.5 8.5M12 4l4.5 4.5M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      <strong>拖拽文件到此处，或点击选择</strong>
      <span class="text-xs text-muted">支持 PDF / Word / Markdown / 文本，单个文件不超过 20MB</span>
      <input
        ref="fileInput"
        class="visually-hidden"
        type="file"
        multiple
        accept=".pdf,.doc,.docx,.md,.markdown,.txt"
        @change="uploadFiles(($event.target as HTMLInputElement).files)"
      />
    </div>

    <div class="card">
      <AppSkeleton v-if="loading" :lines="6" />
      <AppEmpty v-else-if="!materials.length" title="还没有上传资料" description="上传讲义或笔记，AI 助手即可基于内容作答">
        <AppButton size="sm" variant="primary" @click="pickFile">上传资料</AppButton>
      </AppEmpty>

      <ul v-else class="mats__list">
        <li v-for="item in materials" :key="item.id" class="mats__item">
          <span class="mats__icon">{{ item.fileType?.toUpperCase() || 'FILE' }}</span>
          <div class="grow stack" style="gap: 3px">
            <div class="row" style="gap: 6px">
              <span class="truncate" style="font-weight: 500">{{ item.originalName }}</span>
              <AppBadge v-if="item.subject" tone="accent" :text="item.subject" />
            </div>
            <div class="row text-xs text-muted" style="gap: 10px; flex-wrap: wrap">
              <span>{{ item.fileSizeText }}</span>
              <span>{{ item.storageTypeText }}</span>
              <span v-if="item.chunkCount">{{ item.chunkCount }} 个片段</span>
              <span>{{ relativeTime(item.createdAt) }}</span>
              <span v-if="item.errorMessage" style="color: var(--c-danger)">{{ item.errorMessage }}</span>
            </div>
          </div>
          <div class="row" style="gap: 6px">
            <AppBadge :tone="parseTone(item.parseStatus)" :text="`解析：${item.parseStatusText}`" />
            <AppBadge :tone="vectorTone(item.vectorStatus)" :text="`向量：${item.vectorStatusText}`" />
          </div>
          <div class="row" style="gap: 4px">
            <a v-if="item.url" class="mats__link" :href="item.url" target="_blank" rel="noreferrer">下载</a>
            <button
              class="mats__link"
              :disabled="ingestIds.includes(item.id)"
              @click="reIngest(item)"
            >
              {{ ingestIds.includes(item.id) ? '处理中' : '重新解析' }}
            </button>
            <button class="mats__link mats__link--danger" @click="remove(item)">删除</button>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.mats {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
  max-width: var(--content-max);
  margin: 0 auto;
}

.mats__head h2 {
  font-size: 20px;
}

.mats__input {
  width: 160px;
  height: 34px;
  font-size: 13px;
}

.mats__drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: var(--sp-8);
  border: 1.5px dashed var(--c-border-strong);
  border-radius: var(--r-card);
  background: var(--c-surface);
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), background var(--dur-fast) var(--ease),
    transform var(--dur-fast) var(--ease);
}

.mats__drop:hover {
  border-color: var(--c-accent-ring);
  background: var(--c-bg-subtle);
}

.mats__drop.is-over {
  border-color: var(--c-accent);
  background: var(--c-accent-soft);
  transform: translateY(-1px);
}

.mats__list {
  display: flex;
  flex-direction: column;
}

.mats__item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) 8px;
  border-radius: var(--r-control);
  transition: background var(--dur-fast) var(--ease);
}

.mats__item:hover {
  background: var(--c-surface-muted);
}

.mats__icon {
  display: grid;
  place-items: center;
  min-width: 46px;
  height: 34px;
  padding: 0 8px;
  border-radius: 8px;
  background: var(--c-surface-sunken);
  color: var(--c-text-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.mats__link {
  color: var(--c-text-muted);
  font-size: 12.5px;
}

.mats__link:hover {
  color: var(--c-accent-hover);
}

.mats__link--danger:hover {
  color: var(--c-danger);
}

@media (max-width: 860px) {
  .mats__item {
    flex-wrap: wrap;
  }
}
</style>
