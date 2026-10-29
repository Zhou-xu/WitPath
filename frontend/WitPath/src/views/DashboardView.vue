<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppStat from '@/components/ui/AppStat.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import AppProgressRing from '@/components/ui/AppProgressRing.vue'
import AppSelect, { type AppSelectOption } from '@/components/ui/AppSelect.vue'
import TrendAreaChart from '@/components/charts/TrendAreaChart.vue'
import SubjectDonut from '@/components/charts/SubjectDonut.vue'
import { aiApi, dashboardApi, type DashboardVO, type TaskVO } from '@/api'
import { formatMinutes, relativeTime, renderMarkdown, toDateInput } from '@/utils/format'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const rangeOptions: AppSelectOption[] = [
  { label: '近 7 天', value: 7 },
  { label: '近 14 天', value: 14 },
  { label: '近 30 天', value: 30 },
]
const router = useRouter()

const data = ref<DashboardVO | null>(null)
const loading = ref(true)
const loadError = ref('')
const analyzing = ref(false)
const days = ref(14)

const overview = computed(
  () =>
    data.value?.overview ?? {
      totalMinutes: 0,
      last7DaysMinutes: 0,
      todayMinutes: 0,
      streakDays: 0,
      recordCount: 0,
      activeGoalCount: 0,
      todoTaskCount: 0,
      overdueTaskCount: 0,
      doneTaskCount: 0,
      avgFocus: 0,
      avgGoalProgress: 0,
    },
)

const trendDates = computed(() => (data.value?.trend ?? []).map((t) => t.date))
const trendMinutes = computed(() => (data.value?.trend ?? []).map((t) => t.minutes))
const subjects = computed(() => data.value?.subjects ?? [])
const goals = computed(() => data.value?.goals ?? [])
const upcoming = computed(() => data.value?.upcomingTasks ?? [])
const recent = computed(() => data.value?.recentRecords ?? [])

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    data.value = await dashboardApi.overview(days.value)
  } catch (e) {
    loadError.value = (e as Error).message || '概览加载失败'
  } finally {
    loading.value = false
  }
}

async function runAnalysis() {
  analyzing.value = true
  try {
    const task = await aiApi.analyze({ type: 'STUDY_ANALYSIS', rangeDays: days.value })
    ui.toast('AI 分析任务已提交，正在生成洞察', 'success')
    router.push({ name: 'assistant', query: { task: task.taskNo } })
  } catch (e) {
    ui.toast((e as Error).message || 'AI 分析提交失败', 'error')
  } finally {
    analyzing.value = false
  }
}

function statusTone(task: TaskVO): string {
  if (task.overdue) return 'danger'
  if (task.status === 3) return 'success' // TaskStatus.DONE
  return 'info'
}

onMounted(load)
</script>

<template>
  <div class="dash">
    <section class="dash__head row-between">
      <div class="stack" style="gap: 4px">
        <h2>学习概览</h2>
        <span class="text-sm text-muted">
          最近 {{ days }} 天的投入、节奏与目标进度
        </span>
      </div>
      <div class="row" style="gap: 8px">
        <AppSelect v-model="days" :options="rangeOptions" size="sm" @change="load" />
        <AppButton variant="primary" size="sm" :loading="analyzing" @click="runAnalysis">
          生成 AI 分析
        </AppButton>
      </div>
    </section>

    <p v-if="loadError" class="dash__error">
      {{ loadError }}
      <AppButton size="sm" @click="load">重试</AppButton>
    </p>

    <section class="dash__stats">
      <AppStat label="累计学习" :value="formatMinutes(overview.totalMinutes)" :loading="loading">
        <span class="text-xs text-muted">共 {{ overview.recordCount }} 条记录</span>
      </AppStat>
      <AppStat label="近 7 天" :value="formatMinutes(overview.last7DaysMinutes)" :loading="loading">
        <span class="text-xs text-muted">今日 {{ formatMinutes(overview.todayMinutes) }}</span>
      </AppStat>
      <AppStat
        label="连续打卡"
        :value="overview.streakDays"
        unit="天"
        tone="accent"
        :loading="loading"
      >
        <span class="text-xs text-muted">保持节奏比强度更重要</span>
      </AppStat>
      <AppStat
        label="待办任务"
        :value="overview.todoTaskCount"
        unit="项"
        :tone="overview.overdueTaskCount > 0 ? 'danger' : 'default'"
        :loading="loading"
      >
        <span class="text-xs text-muted">
          {{ overview.overdueTaskCount > 0 ? `${overview.overdueTaskCount} 项已逾期` : '无逾期任务' }}
        </span>
      </AppStat>
    </section>

    <section class="dash__grid">
      <div class="card">
        <div class="card__header">
          <div>
            <h3 class="card__title">学习时长趋势</h3>
            <p class="card__desc">按日聚合的学习分钟数</p>
          </div>
          <AppBadge tone="accent" :text="`日均 ${Math.round(overview.last7DaysMinutes / 7)} 分钟`" />
        </div>
        <AppSkeleton v-if="loading" :height="220" block />
        <AppEmpty
          v-else-if="!trendDates.length"
          title="还没有学习记录"
          description="记录一次学习后即可看到趋势曲线"
        >
          <AppButton size="sm" @click="router.push('/app/records')">去记录</AppButton>
        </AppEmpty>
        <TrendAreaChart v-else :dates="trendDates" :minutes="trendMinutes" />
      </div>

      <div class="card">
        <div class="card__header">
          <div>
            <h3 class="card__title">学科分布</h3>
            <p class="card__desc">时间投入占比</p>
          </div>
        </div>
        <AppSkeleton v-if="loading" :height="220" block />
        <AppEmpty v-else-if="!subjects.length" title="暂无学科数据" />
        <SubjectDonut v-else :items="subjects.map((s) => ({ subject: s.subject, minutes: s.minutes }))" />
      </div>
    </section>

    <section class="dash__grid dash__grid--3">
      <div class="card">
        <div class="card__header">
          <div>
            <h3 class="card__title">目标进度</h3>
            <p class="card__desc">进行中的目标</p>
          </div>
          <AppBadge tone="info" :text="`${overview.activeGoalCount} 个进行中`" />
        </div>
        <AppSkeleton v-if="loading" :lines="4" />
        <AppEmpty v-else-if="!goals.length" title="还没有学习目标">
          <AppButton size="sm" @click="router.push('/app/goals')">创建目标</AppButton>
        </AppEmpty>
        <ul v-else class="dash__goals">
          <li v-for="goal in goals" :key="goal.id" class="dash__goal">
            <AppProgressRing :value="goal.progress" :size="46" />
            <div class="grow stack" style="gap: 2px">
              <span class="truncate" style="font-weight: 500">{{ goal.title }}</span>
              <span class="text-xs text-muted">
                {{ goal.actualHours }} / {{ goal.targetHours }} 小时 · 截止 {{ toDateInput(goal.endDate) || '未设置' }}
              </span>
            </div>
            <span class="text-sm" style="color: var(--c-accent-hover)">{{ goal.progress }}%</span>
          </li>
        </ul>
      </div>

      <div class="card">
        <div class="card__header">
          <div>
            <h3 class="card__title">即将到期</h3>
            <p class="card__desc">接下来需要推进的任务</p>
          </div>
        </div>
        <AppSkeleton v-if="loading" :lines="4" />
        <AppEmpty v-else-if="!upcoming.length" title="暂无待办任务" description="计划已清空，可以休息一下" />
        <ul v-else class="dash__tasks">
          <li v-for="task in upcoming" :key="task.id" class="dash__task">
            <div class="grow stack" style="gap: 2px">
              <span class="truncate">{{ task.title }}</span>
              <span class="text-xs text-muted">
                {{ task.goalTitle || '独立任务' }} · 截止 {{ task.dueDate || '未设置' }}
              </span>
            </div>
            <AppBadge :tone="statusTone(task)" :text="task.overdue ? '已逾期' : task.statusText" />
          </li>
        </ul>
      </div>

      <div class="card">
        <div class="card__header">
          <div>
            <h3 class="card__title">最近记录</h3>
            <p class="card__desc">刚刚沉淀的学习内容</p>
          </div>
          <RouterLink class="text-xs" style="color: var(--c-accent-hover)" to="/app/records">
            查看全部
          </RouterLink>
        </div>
        <AppSkeleton v-if="loading" :lines="4" />
        <AppEmpty v-else-if="!recent.length" title="暂无学习记录" />
        <ul v-else class="dash__records">
          <li v-for="rec in recent" :key="rec.id">
            <div class="grow stack" style="gap: 2px">
              <span class="truncate">{{ rec.title }}</span>
              <span class="text-xs text-muted">{{ rec.subject || '未分类' }} · {{ relativeTime(rec.createdAt) }}</span>
            </div>
            <span class="text-sm text-secondary">{{ rec.durationText }}</span>
          </li>
        </ul>
      </div>
    </section>

    <section v-if="data?.latestInsight" class="card dash__insight">
      <div class="card__header">
        <div>
          <h3 class="card__title">最近一次 AI 洞察</h3>
          <p class="card__desc">{{ relativeTime(data.latestInsight.createdAt) }} 生成</p>
        </div>
        <AppButton size="sm" @click="router.push({ name: 'assistant', query: { task: data.latestInsight.taskNo } })">
          查看详情
        </AppButton>
      </div>
      <div class="markdown" v-html="renderMarkdown(data.latestInsight.summary || data.latestInsight.suggestion)" />
    </section>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
  max-width: var(--content-max);
  margin: 0 auto;
}

.dash__head h2 {
  font-size: 20px;
}

.dash__error {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 14px;
  border-radius: var(--r-control);
  background: var(--c-danger-soft);
  color: var(--c-danger);
  font-size: 13px;
}

.dash__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--sp-4);
}

.dash__grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: var(--sp-4);
}

.dash__grid--3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.dash__goals,
.dash__tasks,
.dash__records {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dash__goal,
.dash__task,
.dash__records li {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 8px;
  border-radius: var(--r-control);
  transition: background var(--dur-fast) var(--ease);
}

.dash__goal:hover,
.dash__task:hover,
.dash__records li:hover {
  background: var(--c-surface-muted);
}

.dash__insight .markdown {
  max-height: 240px;
  overflow: auto;
}

.control--sm {
  height: 34px;
  width: auto;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .dash__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dash__grid,
  .dash__grid--3 {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .dash__stats {
    grid-template-columns: 1fr;
  }
}

</style>
