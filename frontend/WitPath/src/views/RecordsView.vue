<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppField from '@/components/ui/AppField.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import AppSelect, { type AppSelectOption } from '@/components/ui/AppSelect.vue'
import { goalApi, recordApi, type GoalVO, type RecordVO } from '@/api'
import { formatDateTime, formatMinutes, toDateInput, toDateTimeLocal } from '@/utils/format'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const records = ref<RecordVO[]>([])
const goals = ref<GoalVO[]>([])
const goalOptions = computed<AppSelectOption[]>(() => [
  { label: '不关联', value: null },
  ...goals.value.map((g) => ({ label: g.title, value: g.id })),
])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const pages = ref(1)
const loading = ref(true)
const saving = ref(false)
const modal = ref(false)

const filters = ref({ subject: '', from: '', to: '', keyword: '' })

const form = ref({
  id: null as number | null,
  goalId: null as number | null,
  title: '',
  content: '',
  subject: '',
  studyDate: toDateInput(new Date()),
  startAt: '',
  endAt: '',
  durationMinutes: 60,
  focusScore: 4,
  outcome: '',
  tags: '',
})

const totalMinutes = computed(() => records.value.reduce((sum, r) => sum + (r.durationMinutes ?? 0), 0))
const autoDuration = computed(() => {
  if (!form.value.startAt || !form.value.endAt) return null
  const diff = (new Date(form.value.endAt).getTime() - new Date(form.value.startAt).getTime()) / 60000
  return diff > 0 ? Math.round(diff) : null
})

async function load() {
  loading.value = true
  try {
    const res = await recordApi.page({
      page: page.value,
      size: size.value,
      subject: filters.value.subject || undefined,
      from: filters.value.from || undefined,
      to: filters.value.to || undefined,
      keyword: filters.value.keyword || undefined,
    })
    records.value = res.records
    total.value = res.total
    pages.value = res.pages
  } catch (e) {
    ui.toast((e as Error).message || '记录加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadGoals() {
  try {
    const res = await goalApi.page({ page: 1, size: 50, status: 1 })
    goals.value = res.records
  } catch {
    goals.value = []
  }
}

function openModal(record?: RecordVO) {
  form.value = record
    ? {
        id: record.id,
        goalId: record.goalId ?? null,
        title: record.title,
        content: record.content ?? '',
        subject: record.subject ?? '',
        studyDate: record.studyDate ?? toDateInput(new Date()),
        startAt: toDateTimeLocal(record.startAt),
        endAt: toDateTimeLocal(record.endAt),
        durationMinutes: record.durationMinutes ?? 60,
        focusScore: record.focusScore ?? 4,
        outcome: record.outcome ?? '',
        tags: record.tags ?? '',
      }
    : {
        id: null,
        goalId: null,
        title: '',
        content: '',
        subject: '',
        studyDate: toDateInput(new Date()),
        startAt: '',
        endAt: '',
        durationMinutes: 60,
        focusScore: 4,
        outcome: '',
        tags: '',
      }
  modal.value = true
}

async function save() {
  if (!form.value.title.trim()) {
    ui.toast('请填写记录标题', 'warning')
    return
  }
  saving.value = true
  try {
    await recordApi.save({
      id: form.value.id,
      goalId: form.value.goalId,
      title: form.value.title,
      content: form.value.content,
      subject: form.value.subject,
      studyDate: form.value.studyDate || null,
      startAt: form.value.startAt || null,
      endAt: form.value.endAt || null,
      durationMinutes: autoDuration.value ?? form.value.durationMinutes,
      focusScore: form.value.focusScore,
      outcome: form.value.outcome,
      tags: form.value.tags,
    })
    ui.toast(form.value.id ? '记录已更新' : '记录已保存', 'success')
    modal.value = false
    await load()
  } catch (e) {
    ui.toast((e as Error).message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function remove(record: RecordVO) {
  if (!confirm(`确认删除记录「${record.title}」？`)) return
  try {
    await recordApi.remove(record.id)
    ui.toast('记录已删除', 'success')
    await load()
  } catch (e) {
    ui.toast((e as Error).message || '删除失败', 'error')
  }
}

function resetFilters() {
  filters.value = { subject: '', from: '', to: '', keyword: '' }
  page.value = 1
  load()
}

watch(page, load)
onMounted(async () => {
  await Promise.all([load(), loadGoals()])
})
</script>

<template>
  <div class="records">
    <section class="records__head row-between">
      <div class="stack" style="gap: 4px">
        <h2>学习记录</h2>
        <span class="text-sm text-muted">共 {{ total }} 条 · 本页累计 {{ formatMinutes(totalMinutes) }}</span>
      </div>
      <AppButton variant="primary" size="sm" @click="openModal()">新增记录</AppButton>
    </section>

    <div class="card">
      <div class="records__filters">
        <input v-model="filters.keyword" class="control records__input" placeholder="搜索标题/内容" @keyup.enter="load" />
        <input v-model="filters.subject" class="control records__input" placeholder="学科" @keyup.enter="load" />
        <input v-model="filters.from" class="control records__input" type="date" />
        <span class="text-muted text-xs">至</span>
        <input v-model="filters.to" class="control records__input" type="date" />
        <AppButton size="sm" @click="load">筛选</AppButton>
        <AppButton size="sm" variant="ghost" @click="resetFilters">重置</AppButton>
      </div>
    </div>

    <div class="card">
      <AppSkeleton v-if="loading" :lines="6" />
      <AppEmpty v-else-if="!records.length" title="暂无学习记录" description="记录今天的学习，趋势与 AI 分析才有依据">
        <AppButton size="sm" variant="primary" @click="openModal()">新增记录</AppButton>
      </AppEmpty>

      <ul v-else class="records__list">
        <li v-for="record in records" :key="record.id" class="records__item">
          <div class="records__date">
            <strong>{{ record.studyDate?.slice(5) || '—' }}</strong>
            <span class="text-xs text-muted">{{ record.durationText }}</span>
          </div>
          <div class="grow stack" style="gap: 4px">
            <div class="row" style="gap: 6px">
              <span style="font-weight: 500">{{ record.title }}</span>
              <AppBadge v-if="record.subject" tone="accent" :text="record.subject" />
              <AppBadge v-if="record.goalTitle" tone="info" :text="record.goalTitle" />
            </div>
            <p v-if="record.content" class="text-sm text-secondary records__content">{{ record.content }}</p>
            <div class="row text-xs text-muted" style="gap: 10px">
              <span>专注度 {{ record.focusScore || '—' }}/5</span>
              <span v-if="record.outcome">成果：{{ record.outcome }}</span>
              <span v-if="record.tags">标签：{{ record.tags }}</span>
              <span>{{ formatDateTime(record.createdAt) }}</span>
            </div>
          </div>
          <div class="row" style="gap: 4px">
            <button class="records__link" @click="openModal(record)">编辑</button>
            <button class="records__link records__link--danger" @click="remove(record)">删除</button>
          </div>
        </li>
      </ul>

      <div v-if="pages > 1" class="records__pager">
        <AppButton size="sm" :disabled="page <= 1" @click="page = page - 1">上一页</AppButton>
        <span class="text-sm text-muted">{{ page }} / {{ pages }}</span>
        <AppButton size="sm" :disabled="page >= pages" @click="page = page + 1">下一页</AppButton>
      </div>
    </div>

    <AppModal :open="modal" :title="form.id ? '编辑学习记录' : '新增学习记录'" @close="modal = false">
      <form class="records__form" @submit.prevent="save">
        <AppField label="标题" required>
          <input v-model="form.title" class="control" placeholder="例如：复习二叉树遍历" />
        </AppField>

        <div class="records__formgrid">
          <AppField label="关联目标">
            <AppSelect v-model="form.goalId" :options="goalOptions" placeholder="不关联" />
          </AppField>
          <AppField label="学科">
            <input v-model="form.subject" class="control" placeholder="如：专业课" />
          </AppField>
          <AppField label="学习日期">
            <input v-model="form.studyDate" class="control" type="date" />
          </AppField>
          <AppField label="专注度（1~5）">
            <input v-model.number="form.focusScore" class="control" type="number" min="1" max="5" />
          </AppField>
          <AppField label="开始时间" hint="填写起止时间会自动计算时长">
            <input v-model="form.startAt" class="control" type="datetime-local" />
          </AppField>
          <AppField label="结束时间">
            <input v-model="form.endAt" class="control" type="datetime-local" />
          </AppField>
        </div>

        <AppField
          label="学习时长（分钟）"
          :hint="autoDuration ? `已按起止时间自动计算为 ${autoDuration} 分钟` : '未填起止时间时使用该值'"
        >
          <input v-model.number="form.durationMinutes" class="control" type="number" min="0" step="5" />
        </AppField>

        <AppField label="学习内容">
          <textarea v-model="form.content" class="control" rows="3" placeholder="今天学了什么？卡在哪里？" />
        </AppField>

        <AppField label="学习成果">
          <input v-model="form.outcome" class="control" placeholder="例如：完成 12 道练习题" />
        </AppField>

        <AppField label="标签" hint="逗号分隔">
          <input v-model="form.tags" class="control" placeholder="算法,复盘" />
        </AppField>

        <div class="row" style="justify-content: flex-end; gap: 8px">
          <AppButton @click="modal = false">取消</AppButton>
          <AppButton type="submit" variant="primary" :loading="saving">保存</AppButton>
        </div>
      </form>
    </AppModal>
  </div>
</template>

<style scoped>
.records {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
  max-width: var(--content-max);
  margin: 0 auto;
}

.records__head h2 {
  font-size: 20px;
}

.records__filters {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.records__input {
  width: 160px;
  height: 34px;
  font-size: 13px;
}

.records__list {
  display: flex;
  flex-direction: column;
}

.records__item {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-4);
  padding: var(--sp-3) 8px;
  border-radius: var(--r-control);
  transition: background var(--dur-fast) var(--ease);
}

.records__item:hover {
  background: var(--c-surface-muted);
}

.records__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 52px;
  padding: 6px 8px;
  border-radius: var(--r-control);
  background: var(--c-surface-sunken);
  color: var(--c-text-secondary);
}

.records__date strong {
  font-size: 15px;
}

.records__content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.records__link {
  color: var(--c-text-muted);
  font-size: 12.5px;
}

.records__link:hover {
  color: var(--c-accent-hover);
}

.records__link--danger:hover {
  color: var(--c-danger);
}

.records__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-border);
  margin-top: var(--sp-2);
}

.records__form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.records__formgrid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
}

@media (max-width: 720px) {
  .records__formgrid {
    grid-template-columns: 1fr;
  }
}
</style>
