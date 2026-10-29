<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppSelect, { type AppSelectOption } from '@/components/ui/AppSelect.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import AppField from '@/components/ui/AppField.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import AiConfigModal from '@/components/ui/AiConfigModal.vue'
import AvatarPickerModal from '@/components/ui/AvatarPickerModal.vue'
import PasswordChangeModal from '@/components/ui/PasswordChangeModal.vue'
import UserAvatar from '@/components/ui/UserAvatar.vue'
import { aiConfigApi, authApi, reminderApi, systemApi, type ReminderVO, type UserAiConfigVO } from '@/api'
import { formatDateTime } from '@/utils/format'
import { useUiStore } from '@/stores/ui'
import { useUserStore } from '@/stores/user'

const ui = useUiStore()

const studyStageOptions: AppSelectOption[] = [
  { label: '高中', value: '高中' },
  { label: '本科', value: '本科' },
  { label: '考研', value: '考研' },
  { label: '研究生', value: '研究生' },
  { label: '职业提升', value: '职业提升' },
]
const user = useUserStore()

const profile = reactive({
  nickname: '',
  email: '',
  phone: '',
  avatarUrl: '',
  studyStage: '',
  studyGoal: '',
  preferredTags: '',
})

const savingProfile = ref(false)
const avatarOpen = ref(false)
const passwordOpen = ref(false)
const aiConfigOpen = ref(false)
const aiConfig = ref<UserAiConfigVO | null>(null)
const health = ref<Record<string, any> | null>(null)
const healthLoading = ref(true)
const reminders = ref<ReminderVO[]>([])
const generating = ref(false)

function fillProfile() {
  const p = user.profile
  if (!p) return
  profile.nickname = p.nickname ?? ''
  profile.email = p.email ?? ''
  profile.phone = p.phone ?? ''
  profile.avatarUrl = p.avatarUrl ?? ''
  profile.studyStage = p.studyStage ?? ''
  profile.studyGoal = p.studyGoal ?? ''
  profile.preferredTags = p.preferredTags ?? ''
}

async function saveProfile() {
  savingProfile.value = true
  try {
    const updated = await authApi.updateProfile({ ...profile })
    user.profile = updated
    ui.toast('资料已保存', 'success')
  } catch (e) {
    ui.toast((e as Error).message || '保存失败', 'error')
  } finally {
    savingProfile.value = false
  }
}

async function loadAiConfig() {
  try {
    aiConfig.value = await aiConfigApi.get()
  } catch {
    aiConfig.value = null
  }
}

async function loadHealth() {
  healthLoading.value = true
  try {
    health.value = await systemApi.health()
  } catch (e) {
    ui.toast((e as Error).message || '健康检查失败', 'error')
  } finally {
    healthLoading.value = false
  }
}

async function loadReminders() {
  try {
    const res = await reminderApi.page({ page: 1, size: 8 })
    reminders.value = res.records
  } catch {
    reminders.value = []
  }
}

async function generateReminders() {
  generating.value = true
  try {
    const count = await reminderApi.generate()
    ui.toast(count > 0 ? `已生成 ${count} 条提醒` : '暂无新的提醒', count > 0 ? 'success' : 'info')
    await loadReminders()
  } catch (e) {
    ui.toast((e as Error).message || '生成失败', 'error')
  } finally {
    generating.value = false
  }
}

const statusTone = (value?: string) => (value === 'UP' || value === 'ok' ? 'success' : 'warning')

onMounted(async () => {
  await user.loadProfile()
  fillProfile()
  await Promise.all([loadHealth(), loadReminders(), loadAiConfig()])
})
</script>

<template>
  <div class="settings">
    <section class="settings__head">
      <h2>个人设置</h2>
      <span class="text-sm text-muted">资料、密码与服务运行状态</span>
    </section>

    <div class="settings__grid">
      <form class="card settings__card settings__card--wide" @submit.prevent="saveProfile">
        <div class="card__header">
          <div>
            <h3 class="card__title">基本资料</h3>
            <p class="card__desc">头像、联系方式与学习目标</p>
          </div>
        </div>

        <AppSkeleton v-if="!user.profile" :lines="5" />
        <template v-else>
          <div class="settings__avatar">
            <UserAvatar :url="user.profile.avatarUrl" :name="user.displayName" :size="56" />
            <div class="stack" style="gap: 2px">
              <strong>{{ user.displayName }}</strong>
              <span class="text-xs text-muted">在线头像或本地图片，保存至阿里云 OSS</span>
            </div>
            <AppButton size="sm" class="settings__avatar-btn" @click="avatarOpen = true">
              修改头像
            </AppButton>
          </div>
          <div class="settings__rows">
            <AppField label="昵称">
              <input v-model="profile.nickname" class="control" />
            </AppField>
            <AppField label="邮箱">
              <input v-model="profile.email" class="control" placeholder="name@example.com" />
            </AppField>
            <AppField label="手机号">
              <input v-model="profile.phone" class="control" />
            </AppField>
            <AppField label="学习阶段">
              <AppSelect v-model="profile.studyStage" :options="studyStageOptions" placeholder="请选择" />
            </AppField>
            <AppField label="偏好标签" hint="逗号分隔">
              <input v-model="profile.preferredTags" class="control" />
            </AppField>
          </div>
          <AppField label="学习目标">
            <textarea v-model="profile.studyGoal" class="control" rows="3" />
          </AppField>
          <div class="row" style="justify-content: flex-end">
            <AppButton type="submit" variant="primary" :loading="savingProfile">保存资料</AppButton>
          </div>
        </template>
      </form>

      <div class="card settings__card">
        <div class="card__header">
          <div>
            <h3 class="card__title">账号安全</h3>
            <p class="card__desc">登录密码与账号保护</p>
          </div>
          <AppButton size="sm" @click="passwordOpen = true">修改密码</AppButton>
        </div>
        <p class="text-sm text-muted">修改成功后当前登录状态保持有效，下次登录请使用新密码。</p>
      </div>

      <div class="card settings__card">
        <div class="card__header">
          <div>
            <h3 class="card__title">AI 模型配置</h3>
            <p class="card__desc">个人大模型与向量模型（AES-GCM 加密存储）</p>
          </div>
          <AppButton size="sm" @click="aiConfigOpen = true">修改模型配置</AppButton>
        </div>
        <p v-if="aiConfig?.configured && !aiConfig.useDefaultApi" class="text-sm">
          当前使用 <strong>{{ aiConfig.model }}</strong>
          <span class="text-muted">（{{ aiConfig.baseUrl }}）</span>
        </p>
        <p v-else class="text-sm text-muted">
          {{ aiConfig?.configured ? '已配置个人模型但已停用，' : '未配置个人模型，' }}当前使用系统默认模型。
        </p>
        <p class="text-xs text-muted">
          API Key 加密后落库，明文仅在服务器内存与 5 分钟缓存中出现；未配置向量模型时使用系统默认。
        </p>
      </div>

      <div class="card settings__card">
        <div class="card__header">
          <div>
            <h3 class="card__title">服务状态</h3>
            <p class="card__desc">后端 / AI / 消息队列 / 存储</p>
          </div>
          <AppButton size="sm" @click="loadHealth">刷新</AppButton>
        </div>

        <AppSkeleton v-if="healthLoading" :lines="4" />
        <ul v-else-if="health" class="settings__health">
          <li>
            <span>后端服务</span>
            <AppBadge :tone="statusTone(health.backend)" :text="health.backend ?? '—'" />
          </li>
          <li>
            <span>AI 服务</span>
            <AppBadge :tone="statusTone(health.ai)" :text="health.ai ?? '—'" />
          </li>
          <li>
            <span>消息队列</span>
            <AppBadge :tone="statusTone(health.mq)" :text="health.mq ?? '—'" />
          </li>
          <li>
            <span>存储</span>
            <AppBadge tone="info" :text="health.storage ?? '—'" />
          </li>
          <li v-if="health.aiDetail?.vector">
            <span>向量库</span>
            <AppBadge
              :tone="String(health.aiDetail.vector.backend).startsWith('milvus') ? 'success' : 'warning'"
              :text="health.aiDetail.vector.backend"
            />
          </li>
          <li v-if="health.aiDetail?.model">
            <span>推理模型</span>
            <AppBadge tone="info" :text="health.aiDetail.model" />
          </li>
        </ul>
        <p v-if="health?.aiDetail?.vector?.backend?.includes('unavailable')" class="text-xs text-muted">
          向量库当前不可用，已自动降级为内存检索，重启 Milvus 后刷新即可恢复。
        </p>
      </div>

      <div class="card settings__card">
        <div class="card__header">
          <div>
            <h3 class="card__title">学习提醒</h3>
            <p class="card__desc">由系统依据目标与任务自动生成</p>
          </div>
          <AppButton size="sm" :loading="generating" @click="generateReminders">生成提醒</AppButton>
        </div>

        <ul v-if="reminders.length" class="settings__reminders">
          <li v-for="item in reminders" :key="item.id">
            <div class="grow stack" style="gap: 2px">
              <span style="font-weight: 500">{{ item.title }}</span>
              <span class="text-xs text-muted">{{ item.content }}</span>
              <span class="text-xs text-muted">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <AppBadge
              :tone="item.level >= 3 ? 'danger' : item.level === 2 ? 'warning' : 'info'"
              :text="item.statusText"
            />
          </li>
        </ul>
        <p v-else class="text-sm text-muted">暂无提醒，点击「生成提醒」根据当前目标与任务生成。</p>
      </div>
    </div>

    <AvatarPickerModal :open="avatarOpen" @close="avatarOpen = false" />
    <PasswordChangeModal :open="passwordOpen" @close="passwordOpen = false" />
    <AiConfigModal :open="aiConfigOpen" @close="aiConfigOpen = false" @saved="aiConfig = $event" />
  </div>
</template>

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  max-width: var(--content-max);
  margin: 0 auto;
}

.settings__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.settings__head h2 {
  font-size: 20px;
}

/* 两列网格：每张卡片都落在固定行内，同行等高，不再出现半行空缺 */
.settings__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-4);
  align-items: stretch;
}

.settings__card {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-5);
}

/* .card__header 自带 margin-bottom，与卡片 flex gap 叠加会撑出 32px，这里归零 */
.settings__card > .card__header {
  margin-bottom: 0;
}

/* 基本资料字段多，独占整行，避免把其余卡片挤成参差的行 */
.settings__card--wide {
  grid-column: 1 / -1;
}

.settings__rows {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-3);
}

.settings__avatar {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 12px;
  border-radius: var(--r-control);
  background: var(--c-bg-subtle);
}

.settings__avatar-btn {
  margin-left: auto;
}

.settings__health,
.settings__reminders {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.settings__health li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: 8px;
  border-radius: var(--r-control);
  font-size: 13px;
  color: var(--c-text-secondary);
}

.settings__health li:hover {
  background: var(--c-surface-muted);
}

/* 提醒条数不定，限高滚动，保证与服务状态卡片保持同一视觉高度 */
.settings__reminders {
  max-height: 272px;
  overflow-y: auto;
  padding-right: 2px;
}

.settings__reminders li {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px 8px;
  border-radius: var(--r-control);
}

.settings__reminders li:hover {
  background: var(--c-surface-muted);
}

@media (max-width: 1080px) {
  .settings__rows {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .settings__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .settings__rows {
    grid-template-columns: 1fr;
  }
}
</style>
