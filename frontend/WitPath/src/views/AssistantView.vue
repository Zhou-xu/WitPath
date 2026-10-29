<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppButton from '@/components/ui/AppButton.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppField from '@/components/ui/AppField.vue'
import AppEmpty from '@/components/ui/AppEmpty.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import AppSelect, { type AppSelectOption } from '@/components/ui/AppSelect.vue'
import {
  aiApi,
  goalApi,
  type AiTaskVO,
  type ChatMessageVO,
  type ConversationVO,
  type GoalVO,
} from '@/api'
import { formatDateTime, relativeTime, renderMarkdown } from '@/utils/format'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const route = useRoute()

/* ------------------------------ 分析任务 ------------------------------ */

const taskTypes = [
  { code: 'STUDY_ANALYSIS', label: '学习态势分析' },
  { code: 'STUDY_PLAN', label: '学习计划生成' },
  { code: 'SUMMARY', label: '阶段学习总结' },
]

const typeOptions: AppSelectOption[] = taskTypes.map((t) => ({ label: t.label, value: t.code }))
const rangeOptions: AppSelectOption[] = [
  { label: '近 7 天', value: 7 },
  { label: '近 14 天', value: 14 },
  { label: '近 30 天', value: 30 },
]

const goals = ref<GoalVO[]>([])
const goalOptions = computed<AppSelectOption[]>(() => [
  { label: '全部进行中目标', value: null },
  ...goals.value.map((g) => ({ label: g.title, value: g.id })),
])
const form = ref({ type: 'STUDY_ANALYSIS', goalId: null as number | null, rangeDays: 14, question: '' })
const task = ref<AiTaskVO | null>(null)
const submitting = ref(false)
const polling = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const running = computed(() => task.value != null && (task.value.status === 0 || task.value.status === 1))

async function submitAnalyze() {
  submitting.value = true
  try {
    const created = await aiApi.analyze({
      type: form.value.type,
      goalId: form.value.goalId ?? undefined,
      rangeDays: form.value.rangeDays,
      question: form.value.question || undefined,
    })
    task.value = created
    ui.toast('任务已提交，正在执行', 'info')
    startPolling(created.taskNo)
  } catch (e) {
    ui.toast((e as Error).message || '提交失败', 'error')
  } finally {
    submitting.value = false
  }
}

function startPolling(taskNo: string) {
  stopPolling()
  polling.value = true
  timer = setInterval(async () => {
    try {
      const detail = await aiApi.taskDetail(taskNo)
      task.value = detail
      if (detail.status >= 2) {
        stopPolling()
        ui.toast(detail.status === 2 ? '分析完成' : '分析失败', detail.status === 2 ? 'success' : 'error')
      }
    } catch {
      stopPolling()
    }
  }, 1500)
}

function stopPolling() {
  if (timer) clearInterval(timer)
  timer = null
  polling.value = false
}

/* ------------------------------ 对话 ------------------------------ */

const conversations = ref<ConversationVO[]>([])
const activeConversation = ref<number | null>(null)
const messages = ref<ChatMessageVO[]>([])
const input = ref('')
const sending = ref(false)
const chatLoading = ref(false)
const scrollEl = ref<HTMLDivElement | null>(null)

async function loadConversations() {
  try {
    conversations.value = await aiApi.conversations()
  } catch {
    conversations.value = []
  }
}

async function loadMessages(id: number | null) {
  activeConversation.value = id
  if (!id) {
    messages.value = []
    return
  }
  chatLoading.value = true
  try {
    messages.value = await aiApi.messages(id)
    scrollToBottom()
  } catch (e) {
    ui.toast((e as Error).message || '消息加载失败', 'error')
  } finally {
    chatLoading.value = false
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  sending.value = true
  messages.value.push({
    // 本地乐观插入的消息：临时用时间戳占位，服务端返回后整体替换
    id: Date.now(),
    conversationId: activeConversation.value ?? 0,
    role: 'user',
    content: text,
    referenced: null,
    tokensUsed: 0,
    createdAt: new Date().toISOString(),
  })
  input.value = ''
  scrollToBottom()
  try {
    const answer = await aiApi.chat({
      conversationId: activeConversation.value,
      message: text,
      withContext: true,
    })
    messages.value.push(answer)
    if (!activeConversation.value) {
      activeConversation.value = answer.conversationId
      await loadConversations()
    }
    scrollToBottom()
  } catch (e) {
    ui.toast((e as Error).message || 'AI 回复失败', 'error')
  } finally {
    sending.value = false
  }
}

async function removeConversation(id: number) {
  if (!confirm('确认删除该会话？')) return
  try {
    await aiApi.removeConversation(id)
    if (activeConversation.value === id) await loadMessages(null)
    await loadConversations()
    ui.toast('会话已删除', 'success')
  } catch (e) {
    ui.toast((e as Error).message || '删除失败', 'error')
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
  })
}

function onEnter(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

/* ------------------------------ 初始化 ------------------------------ */

onMounted(async () => {
  await loadConversations()
  try {
    const res = await goalApi.page({ page: 1, size: 50, status: 1 })
    goals.value = res.records
  } catch {
    goals.value = []
  }
  const taskNo = route.query.task as string | undefined
  if (taskNo) {
    try {
      const detail = await aiApi.taskDetail(taskNo)
      task.value = detail
      if (detail.status < 2) startPolling(taskNo)
    } catch {
      ui.toast('任务不存在或已过期', 'warning')
    }
  } else {
    try {
      task.value = await aiApi.latest()
    } catch {
      task.value = null
    }
  }
})

onBeforeUnmount(stopPolling)

watch(
  () => route.query.task,
  async (taskNo) => {
    if (!taskNo || typeof taskNo !== 'string') return
    try {
      task.value = await aiApi.taskDetail(taskNo)
    } catch {
      /* 忽略 */
    }
  },
)
</script>

<template>
  <div class="assistant">
    <section class="card assistant__task">
      <div class="card__header">
        <div>
          <h3 class="card__title">AI 分析任务</h3>
          <p class="card__desc">基于学习记录与资料，由多 Agent 协作生成洞察</p>
        </div>
        <AppBadge v-if="task" :tone="task.status === 2 ? 'success' : task.status === 3 ? 'danger' : 'info'" :text="task.statusText" />
      </div>

      <div class="assistant__form">
        <AppField label="任务类型">
          <AppSelect v-model="form.type" :options="typeOptions" />
        </AppField>
        <AppField label="聚焦目标">
          <AppSelect v-model="form.goalId" :options="goalOptions" placeholder="全部进行中目标" />
        </AppField>
        <AppField label="数据窗口">
          <AppSelect v-model="form.rangeDays" :options="rangeOptions" />
        </AppField>
      </div>

      <AppField label="补充说明" hint="选填，可描述你最想解决的问题">
        <textarea v-model="form.question" class="control" rows="2" placeholder="例如：最近数学投入不少但正确率没提升，帮我找找原因" />
      </AppField>

      <div class="row" style="justify-content: flex-end; margin-top: var(--sp-3)">
        <AppButton variant="primary" :loading="submitting || polling" @click="submitAnalyze">
          {{ running ? '执行中…' : '提交分析' }}
        </AppButton>
      </div>

      <template v-if="task">
        <hr class="divider" />

        <div v-if="running" class="assistant__progress">
          <div class="progress">
            <div class="progress__bar" :style="{ width: `${task.progress ?? 0}%` }" />
          </div>
          <div class="row-between text-sm">
            <span>{{ task.stageText || '准备中' }}</span>
            <span class="text-muted">{{ task.progress ?? 0 }}%</span>
          </div>
          <span v-if="task.stageMessage" class="text-xs text-muted">{{ task.stageMessage }}</span>
        </div>

        <div v-else-if="task.status === 3" class="assistant__error">
          {{ task.errorMessage || '任务执行失败，请稍后重试' }}
        </div>

        <div v-else class="stack" style="gap: var(--sp-4)">
          <div class="row-between">
            <strong>{{ task.title }}</strong>
            <span class="text-xs text-muted">
              {{ task.model ? `模型 ${task.model} · ` : '' }}{{ task.latencyMs ? `${task.latencyMs}ms` : '' }}
            </span>
          </div>
          <div v-if="task.summary" class="markdown" v-html="renderMarkdown(task.summary)" />
          <div v-if="task.suggestion" class="assistant__suggestion">
            <span class="section-title">行动建议</span>
            <div class="markdown" v-html="renderMarkdown(task.suggestion)" />
          </div>
          <div class="text-xs text-muted">完成于 {{ formatDateTime(task.finishedAt) }}</div>
        </div>
      </template>

      <AppEmpty v-else title="还没有分析任务" description="选择任务类型后提交，即可查看 AI 生成的学习洞察" />
    </section>

    <section class="card assistant__chat">
      <div class="card__header">
        <div>
          <h3 class="card__title">AI 对话</h3>
          <p class="card__desc">结合学习上下文与个人资料库作答</p>
        </div>
        <AppButton size="sm" @click="loadMessages(null)">新会话</AppButton>
      </div>

      <div v-if="conversations.length" class="assistant__convos">
        <button
          class="assistant__convo"
          :class="{ 'is-active': activeConversation === null }"
          @click="loadMessages(null)"
        >
          空白会话
        </button>
        <div v-for="c in conversations" :key="c.id" class="assistant__convo-row">
          <button
            class="assistant__convo grow"
            :class="{ 'is-active': activeConversation === c.id }"
            @click="loadMessages(c.id)"
          >
            <span class="truncate">{{ c.title }}</span>
            <span class="text-xs text-muted">{{ relativeTime(c.lastMessageAt) }}</span>
          </button>
          <button class="assistant__convo-del" title="删除会话" @click="removeConversation(c.id)">×</button>
        </div>
      </div>

      <div ref="scrollEl" class="assistant__messages">
        <AppSkeleton v-if="chatLoading" :lines="4" />
        <AppEmpty v-else-if="!messages.length" title="开始提问吧" description="例如：我这两周的学习节奏有什么问题？" />

        <div v-for="msg in messages" :key="msg.id" class="assistant__msg" :class="`assistant__msg--${msg.role}`">
          <div class="assistant__bubble">
            <div v-if="msg.role === 'assistant'" class="markdown" v-html="renderMarkdown(msg.content)" />
            <span v-else style="white-space: pre-wrap">{{ msg.content }}</span>
            <div
              v-if="msg.role === 'assistant' && Array.isArray(msg.referenced) && msg.referenced.length"
              class="assistant__refs"
            >
              <span class="text-xs text-muted">参考资料</span>
              <span v-for="(ref, idx) in msg.referenced" :key="idx" class="assistant__ref">
                {{ ref.source }} · {{ (ref.score ?? 0).toFixed(2) }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="sending" class="assistant__msg assistant__msg--assistant">
          <div class="assistant__bubble assistant__typing">
            <span /><span /><span />
          </div>
        </div>
      </div>

      <div class="assistant__composer">
        <textarea
          v-model="input"
          class="control"
          rows="2"
          placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
          @keydown="onEnter"
        />
        <AppButton variant="primary" :loading="sending" @click="send">发送</AppButton>
      </div>
    </section>
  </div>
</template>

<style scoped>
.assistant {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: var(--sp-4);
  max-width: var(--content-max);
  margin: 0 auto;
}

.assistant__task {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  /* 面板高度交给内容区（.shell__content）统一滚动，不再自行限高，
     否则 max-height + overflow visible 会让子元素画出卡片外 */
}

.assistant__chat{
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - var(--topbar-h) - var(--sp-16));
}


.assistant__form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-3);
}

.assistant__progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.assistant__error {
  padding: 10px 12px;
  border-radius: var(--r-control);
  background: var(--c-danger-soft);
  color: var(--c-danger);
  font-size: 13px;
}

.assistant__suggestion {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: var(--r-control);
  background: var(--c-accent-soft);
}

.assistant__convos {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 132px;
  overflow: auto;
}

.assistant__convo-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.assistant__convo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--r-control);
  font-size: 13px;
  color: var(--c-text-secondary);
  transition: background var(--dur-fast) var(--ease);
}

.assistant__convo:hover {
  background: var(--c-surface-muted);
}

.assistant__convo.is-active {
  background: var(--c-accent-soft);
  color: var(--c-accent-hover);
}

.assistant__convo-del {
  color: var(--c-text-muted);
  font-size: 15px;
  padding: 0 6px;
}

.assistant__convo-del:hover {
  color: var(--c-danger);
}

.assistant__messages {
  flex: 1;
  min-height: 240px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-2) 4px;
  background: var(--c-bg-subtle);
  border-radius: var(--r-control);
}

.assistant__msg {
  display: flex;
}

.assistant__msg--user {
  justify-content: flex-end;
}

.assistant__bubble {
  max-width: 86%;
  padding: 10px 14px;
  border-radius: 14px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  font-size: 13.5px;
  line-height: 1.7;
}

.assistant__msg--user .assistant__bubble {
  background: var(--c-accent);
  border-color: var(--c-accent);
  color: var(--c-text-inverse);
}

.assistant__refs {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--c-border-strong);
}

.assistant__ref {
  font-size: 12px;
  color: var(--c-text-muted);
}

.assistant__typing {
  display: flex;
  gap: 4px;
  align-items: center;
}

.assistant__typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-text-muted);
  animation: assistant-blink 1.2s infinite ease-in-out;
}

.assistant__typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.assistant__typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes assistant-blink {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

.assistant__composer {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-2);
}

.assistant__composer textarea {
  resize: none;
}

@media (max-width: 1000px) {
  .assistant {
    grid-template-columns: 1fr;
  }
  .assistant__task,
  .assistant__chat {
    max-height: none;
  }
  .assistant__form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .assistant__form {
    grid-template-columns: 1fr;
  }
}

</style>
