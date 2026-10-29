<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppField from '@/components/ui/AppField.vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import AppProgressRing from '@/components/ui/AppProgressRing.vue'
import AppSelect, { type AppSelectOption } from '@/components/ui/AppSelect.vue'
import { goalApi, taskApi, type GoalVO, type TaskVO } from '@/api'
import { formatHours, toDateInput } from '@/utils/format'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

const goals = ref<GoalVO[]>([])
const tasks = ref<TaskVO[]>([])
const loading = ref(true)
const taskLoading = ref(false)
const keyword = ref('')
const statusFilter = ref<number | ''>('')

const statusOptions: AppSelectOption[] = [
  { label: '全部状态', value: '' },
  { label: '进行中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已暂停', value: 3 },
]
const priorityOptions: AppSelectOption[] = [
  { label: '低', value: 1 },
  { label: '中', value: 2 },
  { label: '高', value: 3 },
]
const taskGoalOptions = computed<AppSelectOption[]>(() => [
  { label: '独立任务', value: null },
  ...goals.value.map((g) => ({ label: g.title, value: g.id })),
])
const activeId = ref<number | null>(null)

const goalModal = ref(false)
const taskModal = ref(false)
const saving = ref(false)

const goalForm = ref({
  id: null as number | null,
  title: '',
  description: '',
  subject: '',
  priority: 2,
  startDate: toDateInput(new Date()),
  endDate: '',
  targetHours: 20,
})

const taskForm = ref({
  id: null as number | null,
  goalId: null as number | null,
  title: '',
  description: '',
  priority: 2,
  planDate: toDateInput(new Date()),
  dueDate: '',
  estimatedMinutes: 60,
})

const activeGoal = computed(() => goals.value.find((g) => g.id === activeId.value) ?? null)

const priorityText = (p: number) => (p === 3 ? '高' : p === 1 ? '低' : '中')
const priorityTone = (p: number) => (p === 3 ? 'warning' : p === 1 ? 'default' : 'info')
// 与后端枚举保持一致：GoalStatus 1 进行中 / 2 已完成 / 3 已暂停 / 4 已归档
const goalTone = (status: number) => (status === 2 ? 'success' : status === 3 ? 'violet' : 'accent')
// TaskStatus 1 待办 / 2 进行中 / 3 已完成 / 4 已延期
const TASK_DONE = 3
const isDone = (task: TaskVO) => task.status === TASK_DONE

async function loadGoals() {
  loading.value = true
  try {
    const res = await goalApi.page({
      page: 1,
      size: 50,
      keyword: keyword.value || undefined,
      status: statusFilter.value === '' ? undefined : statusFilter.value,
    })
    goals.value = res.records
    if (!activeId.value && goals.value.length) activeId.value = goals.value[0]?.id ?? null
    if (activeId.value && !goals.value.some((g) => g.id === activeId.value)) {
      activeId.value = goals.value[0]?.id ?? null
    }
  } catch (e) {
    ui.toast((e as Error).message || '目标加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadTasks() {
  if (!activeId.value) {
    tasks.value = []
    return
  }
  taskLoading.value = true
  try {
    const res = await taskApi.page({ page: 1, size: 50, goalId: activeId.value })
    tasks.value = res.records
  } catch (e) {
    ui.toast((e as Error).message || '任务加载失败', 'error')
  } finally {
    taskLoading.value = false
  }
}

watch(activeId, loadTasks)
onMounted(loadGoals)

function openGoalModal(goal?: GoalVO) {
  goalForm.value = goal
    ? {
        id: goal.id,
        title: goal.title,
        description: goal.description ?? '',
        subject: goal.subject ?? '',
        priority: goal.priority ?? 2,
        startDate: goal.startDate ?? '',
        endDate: goal.endDate ?? '',
        targetHours: Number(goal.targetHours ?? 0),
      }
    : {
        id: null,
        title: '',
        description: '',
        subject: '',
        priority: 2,
        startDate: toDateInput(new Date()),
        endDate: '',
        targetHours: 20,
      }
  goalModal.value = true
}

function openTaskModal(task?: TaskVO) {
  taskForm.value = task
    ? {
        id: task.id,
        goalId: task.goalId ?? activeId.value,
        title: task.title,
        description: task.description ?? '',
        priority: task.priority ?? 2,
        planDate: task.planDate ?? '',
        dueDate: task.dueDate ?? '',
        estimatedMinutes: task.estimatedMinutes ?? 60,
      }
    : {
        id: null,
        goalId: activeId.value,
        title: '',
        description: '',
        priority: 2,
        planDate: toDateInput(new Date()),
        dueDate: '',
        estimatedMinutes: 60,
      }
  taskModal.value = true
}

async function saveGoal() {
  if (!goalForm.value.title.trim()) {
    ui.toast('请填写目标标题', 'warning')
    return
  }
  saving.value = true
  try {
    await goalApi.save({
      id: goalForm.value.id,
      title: goalForm.value.title,
      description: goalForm.value.description,
      subject: goalForm.value.subject,
      priority: goalForm.value.priority,
      startDate: goalForm.value.startDate || null,
      endDate: goalForm.value.endDate || null,
      targetHours: goalForm.value.targetHours,
    })
    ui.toast(goalForm.value.id ? '目标已更新' : '目标已创建', 'success')
    goalModal.value = false
    await loadGoals()
    await loadTasks()
  } catch (e) {
    ui.toast((e as Error).message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function saveTask() {
  if (!taskForm.value.title.trim()) {
    ui.toast('请填写任务标题', 'warning')
    return
  }
  saving.value = true
  try {
    await taskApi.save({
      id: taskForm.value.id,
      goalId: taskForm.value.goalId,
      title: taskForm.value.title,
      description: taskForm.value.description,
      priority: taskForm.value.priority,
      planDate: taskForm.value.planDate || null,
      dueDate: taskForm.value.dueDate || null,
      estimatedMinutes: taskForm.value.estimatedMinutes,
    })
    ui.toast(taskForm.value.id ? '任务已更新' : '任务已创建', 'success')
    taskModal.value = false
    await loadTasks()
    await loadGoals()
  } catch (e) {
    ui.toast((e as Error).message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function toggleTask(task: TaskVO) {
  try {
    await taskApi.changeStatus(task.id, isDone(task) ? 1 : TASK_DONE)
    await loadTasks()
    await loadGoals()
  } catch (e) {
    ui.toast((e as Error).message || '状态更新失败', 'error')
  }
}

async function removeGoal(goal: GoalVO) {
  if (!confirm(`确认删除目标「${goal.title}」？其下任务会一并移除。`)) return
  try {
    await goalApi.remove(goal.id)
    ui.toast('目标已删除', 'success')
    if (activeId.value === goal.id) activeId.value = null
    await loadGoals()
    await loadTasks()
  } catch (e) {
    ui.toast((e as Error).message || '删除失败', 'error')
  }
}

async function removeTask(task: TaskVO) {
  if (!confirm(`确认删除任务「${task.title}」？`)) return
  try {
    await taskApi.remove(task.id)
    ui.toast('任务已删除', 'success')
    await loadTasks()
    await loadGoals()
  } catch (e) {
    ui.toast((e as Error).message || '删除失败', 'error')
  }
}

async function changeGoalStatus(goal: GoalVO, status: number) {
  try {
    await goalApi.changeStatus(goal.id, status)
    ui.toast('目标状态已更新', 'success')
    await loadGoals()
  } catch (e) {
    ui.toast((e as Error).message || '状态更新失败', 'error')
  }
}
</script>

<template>
  <div class="goals">
    <section class="goals__head row-between">
      <div class="stack" style="gap: 4px">
        <h2>目标与任务</h2>
        <span class="text-sm text-muted">先定目标，再拆任务，进度由记录自动回算</span>
      </div>
      <div class="row" style="gap: 8px">
        <input v-model="keyword" class="control goals__search" placeholder="搜索目标" @keyup.enter="loadGoals" />
        <AppSelect v-model="statusFilter" :options="statusOptions" @change="loadGoals" />
        <AppButton variant="primary" size="sm" @click="openGoalModal()">新建目标</AppButton>
      </div>
    </section>

    <div class="goals__body">
      <div class="card goals__list">
        <div class="card__header">
          <h3 class="card__title">目标列表</h3>
          <span class="text-xs text-muted">{{ goals.length }} 个</span>
        </div>

        <AppSkeleton v-if="loading" :lines="6" />
        <AppEmpty v-else-if="!goals.length" title="还没有目标" description="创建一个目标，开始拆解学习任务">
          <AppButton size="sm" variant="primary" @click="openGoalModal()">新建目标</AppButton>
        </AppEmpty>

        <ul v-else class="goals__items">
          <li
            v-for="goal in goals"
            :key="goal.id"
            class="goals__item"
            :class="{ 'is-active': goal.id === activeId }"
            @click="activeId = goal.id"
          >
            <AppProgressRing :value="goal.progress" :size="44" />
            <div class="grow stack" style="gap: 2px">
              <div class="row" style="gap: 6px">
                <span class="truncate" style="font-weight: 500">{{ goal.title }}</span>
                <AppBadge :tone="goalTone(goal.status)" :text="goal.statusText" />
              </div>
              <span class="text-xs text-muted">
                {{ goal.subject || '未分类' }} · {{ formatHours(goal.actualHours * 60) }} /
                {{ goal.targetHours }} 小时
              </span>
              <span v-if="goal.overdue" class="text-xs" style="color: var(--c-danger)">已超过截止日期</span>
            </div>
          </li>
        </ul>
      </div>

      <div class="card goals__tasks">
        <div class="card__header">
          <div>
            <h3 class="card__title">{{ activeGoal ? activeGoal.title : '任务' }}</h3>
            <p class="card__desc">
              {{ activeGoal ? `任务 ${activeGoal.taskDone}/${activeGoal.taskTotal} 已完成` : '选择左侧目标查看任务' }}
            </p>
          </div>
          <AppButton v-if="activeGoal" size="sm" @click="openTaskModal()">添加任务</AppButton>
        </div>

        <div v-if="activeGoal" class="goals__goalmeta">
          <span>{{ activeGoal.description || '暂无描述' }}</span>
          <div class="row" style="gap: 6px">
            <AppButton size="sm" @click="openGoalModal(activeGoal)">编辑</AppButton>
            <AppButton v-if="activeGoal.status !== 2" size="sm" @click="changeGoalStatus(activeGoal, 2)">
              标记完成
            </AppButton>
            <AppButton v-else size="sm" @click="changeGoalStatus(activeGoal, 1)">重新开启</AppButton>
            <AppButton size="sm" variant="danger" @click="removeGoal(activeGoal)">删除</AppButton>
          </div>
        </div>

        <AppSkeleton v-if="taskLoading" :lines="5" />
        <AppEmpty v-else-if="!activeGoal" title="未选择目标" description="点击左侧目标卡片查看并管理任务" />
        <AppEmpty v-else-if="!tasks.length" title="该目标下暂无任务">
          <AppButton size="sm" variant="primary" @click="openTaskModal()">添加任务</AppButton>
        </AppEmpty>

        <ul v-else class="goals__tasklist">
          <li v-for="task in tasks" :key="task.id" class="goals__taskrow">
            <button
              class="goals__check"
              :class="{ 'is-done': isDone(task) }"
              :aria-label="isDone(task) ? '标记为未完成' : '标记为已完成'"
              @click="toggleTask(task)"
            >
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M2.5 6.2l2.4 2.4L9.6 3.9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
            <div class="grow stack" style="gap: 2px">
              <span :style="isDone(task) ? 'text-decoration: line-through; color: var(--c-text-muted)' : ''">
                {{ task.title }}
              </span>
              <span class="text-xs text-muted">
                截止 {{ task.dueDate || '未设置' }} · 预计 {{ task.estimatedMinutes }} 分钟 · 实际
                {{ task.actualMinutes }} 分钟
              </span>
            </div>
            <AppBadge :tone="priorityTone(task.priority)" :text="`优先级${priorityText(task.priority)}`" />
            <AppBadge v-if="task.overdue" tone="danger" text="逾期" />
            <div class="row" style="gap: 4px">
              <button class="goals__linkbtn" @click="openTaskModal(task)">编辑</button>
              <button class="goals__linkbtn goals__linkbtn--danger" @click="removeTask(task)">删除</button>
            </div>
          </li>
        </ul>
      </div>
    </div>

    <AppModal :open="goalModal" :title="goalForm.id ? '编辑目标' : '新建目标'" @close="goalModal = false">
      <form class="goals__form" @submit.prevent="saveGoal">
        <AppField label="目标标题" required>
          <input v-model="goalForm.title" class="control" placeholder="例如：完成数据结构系统复习" />
        </AppField>
        <div class="goals__formgrid">
          <AppField label="学科">
            <input v-model="goalForm.subject" class="control" placeholder="如：专业课" />
          </AppField>
          <AppField label="优先级">
            <AppSelect v-model="goalForm.priority" :options="priorityOptions" />
          </AppField>
          <AppField label="开始日期">
            <input v-model="goalForm.startDate" class="control" type="date" />
          </AppField>
          <AppField label="截止日期">
            <input v-model="goalForm.endDate" class="control" type="date" />
          </AppField>
        </div>
        <AppField label="目标时长（小时）">
          <input v-model.number="goalForm.targetHours" class="control" type="number" min="0" step="1" />
        </AppField>
        <AppField label="目标描述">
          <textarea v-model="goalForm.description" class="control" rows="3" placeholder="为什么想达成它？衡量标准是什么？" />
        </AppField>
        <div class="row" style="justify-content: flex-end; gap: 8px">
          <AppButton @click="goalModal = false">取消</AppButton>
          <AppButton type="submit" variant="primary" :loading="saving">保存</AppButton>
        </div>
      </form>
    </AppModal>

    <AppModal :open="taskModal" :title="taskForm.id ? '编辑任务' : '添加任务'" @close="taskModal = false">
      <form class="goals__form" @submit.prevent="saveTask">
        <AppField label="任务标题" required>
          <input v-model="taskForm.title" class="control" placeholder="例如：完成二叉树章节练习" />
        </AppField>
        <div class="goals__formgrid">
          <AppField label="所属目标">
            <AppSelect v-model="taskForm.goalId" :options="taskGoalOptions" placeholder="独立任务" />
          </AppField>
          <AppField label="优先级">
            <AppSelect v-model="taskForm.priority" :options="priorityOptions" />
          </AppField>
          <AppField label="计划日期">
            <input v-model="taskForm.planDate" class="control" type="date" />
          </AppField>
          <AppField label="截止日期">
            <input v-model="taskForm.dueDate" class="control" type="date" />
          </AppField>
        </div>
        <AppField label="预计时长（分钟）">
          <input v-model.number="taskForm.estimatedMinutes" class="control" type="number" min="0" step="10" />
        </AppField>
        <AppField label="任务描述">
          <textarea v-model="taskForm.description" class="control" rows="2" />
        </AppField>
        <div class="row" style="justify-content: flex-end; gap: 8px">
          <AppButton @click="taskModal = false">取消</AppButton>
          <AppButton type="submit" variant="primary" :loading="saving">保存</AppButton>
        </div>
      </form>
    </AppModal>
  </div>
</template>

<style scoped>
.goals {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
  max-width: var(--content-max);
  margin: 0 auto;
}

.goals__head h2 {
  font-size: 20px;
}

.goals__search {
  width: 180px;
  height: 34px;
  font-size: 13px;
}

.goals__select {
  width: 120px;
  height: 34px;
  font-size: 13px;
}

.goals__body {
  display: grid;
  grid-template-columns: minmax(280px, 380px) 1fr;
  gap: var(--sp-4);
  align-items: start;
}

.goals__items {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.goals__item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px;
  border-radius: var(--r-control);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease);
}

.goals__item:hover {
  background: var(--c-surface-muted);
}

.goals__item.is-active {
  background: var(--c-accent-soft);
  border-color: var(--c-accent-ring);
}

.goals__goalmeta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: 10px 12px;
  margin-bottom: var(--sp-2);
  border-radius: var(--r-control);
  background: var(--c-surface-muted);
  color: var(--c-text-secondary);
  font-size: 13px;
}

.goals__tasklist {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.goals__taskrow {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 8px;
  border-radius: var(--r-control);
  transition: background var(--dur-fast) var(--ease);
}

.goals__taskrow:hover {
  background: var(--c-surface-muted);
}

.goals__check {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  border: 1.5px solid var(--c-border-strong);
  color: transparent;
  transition: all var(--dur-fast) var(--ease);
}

.goals__check:hover {
  border-color: var(--c-accent);
}

.goals__check.is-done {
  background: var(--c-accent);
  border-color: var(--c-accent);
  color: #fffaf4;
}

.goals__linkbtn {
  color: var(--c-text-muted);
  font-size: 12.5px;
}

.goals__linkbtn:hover {
  color: var(--c-accent-hover);
}

.goals__linkbtn--danger:hover {
  color: var(--c-danger);
}

.goals__form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.goals__formgrid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
}

@media (max-width: 1000px) {
  .goals__body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .goals__formgrid {
    grid-template-columns: 1fr;
  }
}


@media (max-width: 900px) {
  .goals__body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .goals__head {
    flex-wrap: wrap;
  }

  .goals__head .row {
    flex-wrap: wrap;
    width: 100%;
  }

  .goals__head .asel {
    width: 132px;
    flex: none;
  }

  .goals__search {
    flex: 1;
    width: auto;
    min-width: 0;
  }
}

</style>
