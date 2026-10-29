/** 全部后端接口定义（与 Spring Boot Controller 一一对应） */

import { http, httpRoot, qs, unwrap, type ApiResult } from './request'

/* ------------------------------ 类型 ------------------------------ */

export interface PageResult<T> {
  page: number
  size: number
  total: number
  pages: number
  records: T[]
}

export interface UserVO {
  id: number
  username: string
  nickname: string
  avatarUrl: string
  email: string
  phone: string
  role: number
  roleName: string
  status: number
  studyStage: string
  studyGoal: string
  preferredTags: string
  lastLoginAt: string
  createdAt: string
}

export interface LoginVO {
  token: string
  userId: number
  username: string
  nickname: string
  avatar: string
  role: number
  expire: number
}

export interface AvatarPresetVO {
  id: string
  name: string
  /** SVG 源码，前端编码为 data URI 预览；后端用同一份 SVG 落 OSS，预览即成品 */
  svg: string
}

export interface CaptchaVO {
  /** 雪花 ID，19 位大整数，超出 JS 安全整数范围，必须按字符串处理 */
  captchaKey: string
  captchaImage: string
}

export interface GoalVO {
  id: number
  title: string
  description: string
  subject: string
  priority: number
  status: number
  statusText: string
  startDate: string
  endDate: string
  targetHours: number
  actualHours: number
  taskTotal: number
  taskDone: number
  progress: number
  daysLeft: number | null
  overdue: boolean
  createdAt: string
}

export interface TaskVO {
  id: number
  goalId: number | null
  goalTitle: string
  title: string
  description: string
  status: number
  statusText: string
  priority: number
  planDate: string
  dueDate: string
  estimatedMinutes: number
  actualMinutes: number
  completedAt: string
  sortOrder: number
  overdue: boolean
  daysLeft: number | null
  createdAt: string
}

export interface RecordVO {
  id: number
  goalId: number | null
  goalTitle: string
  taskId: number | null
  taskTitle: string
  title: string
  content: string
  subject: string
  studyDate: string
  startAt: string
  endAt: string
  durationMinutes: number
  durationText: string
  focusScore: number
  outcome: string
  tags: string
  createdAt: string
}

export interface MaterialVO {
  id: number
  name: string
  originalName: string
  fileType: string
  fileSize: number
  fileSizeText: string
  storageType: number
  storageTypeText: string
  subject: string
  description: string
  parseStatus: number
  parseStatusText: string
  vectorStatus: number
  vectorStatusText: string
  chunkCount: number
  errorMessage: string
  url: string
  createdAt: string
}

export interface AiTaskVO {
  id: number
  taskNo: string
  type: string
  title: string
  question: string
  status: number
  statusText: string
  stage: string
  stageText: string
  stageMessage: string
  progress: number
  summary: string
  suggestion: string
  result: unknown
  model: string
  tokensUsed: number
  latencyMs: number
  errorMessage: string
  startedAt: string
  finishedAt: string
  createdAt: string
}

export interface ChatMessageVO {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  referenced: Array<{ source: string; snippet: string; score: number }> | null
  tokensUsed: number
  createdAt: string
}

export interface ConversationVO {
  id: number
  title: string
  messageCount: number
  lastMessageAt: string
  createdAt: string
}

export interface ReminderVO {
  id: number
  goalId: number | null
  taskId: number | null
  type: string
  level: number
  title: string
  content: string
  source: string
  status: number
  statusText: string
  triggerTime: string
  readAt: string
  createdAt: string
}

export interface DashboardVO {
  overview: {
    totalMinutes: number
    last7DaysMinutes: number
    todayMinutes: number
    streakDays: number
    recordCount: number
    activeGoalCount: number
    todoTaskCount: number
    overdueTaskCount: number
    doneTaskCount: number
    avgFocus: number
    avgGoalProgress: number
  }
  trend: Array<{ date: string; minutes: number; records: number; focus: number }>
  subjects: Array<{ subject: string; minutes: number; records: number; ratio: number }>
  focusTrend: Array<{ date: string; minutes: number; records: number; focus: number }>
  goals: Array<{
    id: number
    title: string
    progress: number
    targetHours: number
    actualHours: number
    status: number
    endDate: string
  }>
  upcomingTasks: TaskVO[]
  recentRecords: RecordVO[]
  latestInsight: {
    taskId: number
    taskNo: string
    type: string
    summary: string
    suggestion: string
    createdAt: string
  } | null
  unreadReminders: number
}

/* ------------------------------ 认证 ------------------------------ */

export const authApi = {
  /** 第一步：创建登录会话（返回字符串形式的雪花 ID，避免 JS 精度丢失） */
  async initSession(): Promise<string> {
    return unwrap(httpRoot.get('/login/LoginInitId'))
  },
  /** 第二步：获取验证码（header 需要 userId） */
  async captcha(loginInitId: string): Promise<CaptchaVO> {
    return unwrap(httpRoot.post('/login/image', null, { headers: { userId: loginInitId } }))
  },
  /** 第三步：登录 */
  async login(payload: {
    loginInitId: string
    username: string
    password: string
    captchaAnswer: string
  }): Promise<LoginVO> {
    return unwrap(httpRoot.post('/login', payload))
  },
  async register(payload: Record<string, unknown>): Promise<UserVO> {
    return unwrap(httpRoot.post('/register', payload))
  },
  async profile(): Promise<UserVO> {
    return unwrap(http.get('/user/profile'))
  },
  async updateProfile(payload: Partial<UserVO>): Promise<UserVO> {
    return unwrap(http.put('/user/profile', payload))
  },
  async changePassword(payload: { oldPassword: string; newPassword: string }): Promise<boolean> {
    return unwrap(http.post('/user/password', payload))
  },
}

/* ------------------------------ 头像 ------------------------------ */

export const avatarApi = {
  /** 内置在线头像列表（后端生成 SVG，不依赖外网） */
  async presets(): Promise<AvatarPresetVO[]> {
    return unwrap(http.get('/user/avatar/presets'))
  },
  /** 本地上传：直传后端再转存阿里云 OSS（公共读直链） */
  async upload(file: File): Promise<UserVO> {
    const form = new FormData()
    form.append('file', file)
    return unwrap(
      http.post('/user/avatar', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 60000,
      }),
    )
  },
  /** 选用内置头像 */
  async applyPreset(presetId: string): Promise<UserVO> {
    return unwrap(http.post('/user/avatar/preset', { presetId }))
  },
}

/* ----------------------- 个人大模型配置 ----------------------- */

/** 模型下拉选项 */
export interface ModelOptionVO {
  id: string
  name: string
}

export interface UserAiConfigVO {
  configured: boolean
  provider: string | null
  baseUrl: string | null
  model: string | null
  /** API Key 掩码（后端不返回明文） */
  apiKeyMasked: string | null
  embeddingProvider: string | null
  embeddingModel: string | null
  embeddingBaseUrl: string | null
  embeddingApiKeyMasked: string | null
  embeddingOverridden: boolean
  /** 是否使用系统默认模型（true 时个人配置保留但不生效） */
  useDefaultApi: boolean
  updatedAt: string | null
}

export const aiConfigApi = {
  /** 当前配置（API Key 为掩码）；未配置时返回 configured=false */
  async get(): Promise<UserAiConfigVO> {
    return unwrap(http.get('/user/ai-config'))
  },
  /** 保存配置（API Key 留空表示不修改已保存的值） */
  async save(payload: {
    provider?: string
    apiKey: string
    baseUrl: string
    model: string
    embeddingProvider?: string
    embeddingModel?: string
    embeddingApiKey?: string
    embeddingBaseUrl?: string
  }): Promise<UserAiConfigVO> {
    return unwrap(http.put('/user/ai-config', payload))
  },
  /** 切换回系统默认模型（个人配置保留，可随时改回） */
  async useDefault(): Promise<UserAiConfigVO> {
    return unwrap(http.post('/user/ai-config/use-default'))
  },
  /** 删除配置，恢复使用系统默认模型 */
  async remove(): Promise<boolean> {
    return unwrap(http.delete('/user/ai-config'))
  },
  /** 拉取用户端点上的模型列表（后端代理，避免跨域与 Key 外泄） */
  async models(payload: { baseUrl: string; apiKey: string }): Promise<ModelOptionVO[]> {
    return unwrap(http.post('/user/ai-config/models', payload, { timeout: 30000 }))
  },
}

/* ------------------------------ 概览 ------------------------------ */

export const dashboardApi = {
  async overview(days = 14): Promise<DashboardVO> {
    return unwrap(http.get(`/dashboard/overview${qs({ days })}`))
  },
}

/* ------------------------------ 目标 / 任务 / 记录 ------------------------------ */

export const goalApi = {
  async page(params: { page?: number; size?: number; keyword?: string; status?: number }) {
    return unwrap(http.get<ApiResult<PageResult<GoalVO>>>(`/goals/page${qs(params)}`))
  },
  async save(payload: Record<string, unknown>): Promise<GoalVO> {
    return unwrap(http.post('/goals', payload))
  },
  async remove(id: number): Promise<boolean> {
    return unwrap(http.delete(`/goals/${id}`))
  },
  async changeStatus(id: number, status: number): Promise<GoalVO> {
    return unwrap(http.post(`/goals/${id}/status${qs({ status })}`))
  },
}

export const taskApi = {
  async page(params: Record<string, unknown>) {
    return unwrap(http.get<ApiResult<PageResult<TaskVO>>>(`/tasks/page${qs(params)}`))
  },
  async upcoming(limit = 6) {
    return unwrap(http.get<ApiResult<TaskVO[]>>(`/tasks/upcoming${qs({ limit })}`))
  },
  async save(payload: Record<string, unknown>): Promise<TaskVO> {
    return unwrap(http.post('/tasks', payload))
  },
  async remove(id: number): Promise<boolean> {
    return unwrap(http.delete(`/tasks/${id}`))
  },
  async changeStatus(id: number, status: number): Promise<TaskVO> {
    return unwrap(http.post(`/tasks/${id}/status${qs({ status })}`))
  },
}

export const recordApi = {
  async page(params: Record<string, unknown>) {
    return unwrap(http.get<ApiResult<PageResult<RecordVO>>>(`/records/page${qs(params)}`))
  },
  async recent(limit = 8) {
    return unwrap(http.get<ApiResult<RecordVO[]>>(`/records/recent${qs({ limit })}`))
  },
  async save(payload: Record<string, unknown>): Promise<RecordVO> {
    return unwrap(http.post('/records', payload))
  },
  async remove(id: number): Promise<boolean> {
    return unwrap(http.delete(`/records/${id}`))
  },
}

/* ------------------------------ 资料 ------------------------------ */

export const materialApi = {
  async page(params: Record<string, unknown>) {
    return unwrap(http.get<ApiResult<PageResult<MaterialVO>>>(`/materials/page${qs(params)}`))
  },
  async upload(file: File, subject?: string, description?: string): Promise<MaterialVO> {
    const form = new FormData()
    form.append('file', file)
    if (subject) form.append('subject', subject)
    if (description) form.append('description', description)
    return unwrap(
      http.post('/materials/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 120000,
      }),
    )
  },
  async update(id: number, payload: Record<string, unknown>): Promise<MaterialVO> {
    return unwrap(http.put(`/materials/${id}`, payload))
  },
  async remove(id: number): Promise<boolean> {
    return unwrap(http.delete(`/materials/${id}`))
  },
  async ingest(id: number): Promise<boolean> {
    return unwrap(http.post(`/materials/${id}/ingest`))
  },
}

/* ------------------------------ AI ------------------------------ */

export const aiApi = {
  async analyze(payload: { type?: string; goalId?: number; rangeDays?: number; question?: string }) {
    return unwrap(http.post<ApiResult<AiTaskVO>>('/ai/analyze', payload))
  },
  async tasks(params: Record<string, unknown> = {}) {
    return unwrap(http.get<ApiResult<PageResult<AiTaskVO>>>(`/ai/tasks${qs(params)}`))
  },
  async taskDetail(taskNo: string): Promise<AiTaskVO> {
    return unwrap(http.get(`/ai/tasks/${taskNo}`))
  },
  async latest(type?: string): Promise<AiTaskVO | null> {
    return unwrap(http.get(`/ai/tasks/latest${qs({ type })}`))
  },
  async chat(payload: { conversationId?: number | null; message: string; withContext?: boolean }) {
    return unwrap(http.post<ApiResult<ChatMessageVO>>('/ai/chat', payload))
  },
  async conversations(): Promise<ConversationVO[]> {
    return unwrap(http.get('/ai/conversations'))
  },
  async messages(conversationId: number): Promise<ChatMessageVO[]> {
    return unwrap(http.get(`/ai/conversations/${conversationId}/messages`))
  },
  async removeConversation(conversationId: number): Promise<boolean> {
    return unwrap(http.delete(`/ai/conversations/${conversationId}`))
  },
  async search(q: string, topK = 5): Promise<unknown> {
    return unwrap(http.get(`/ai/search${qs({ q, topK })}`))
  },
}

/* ------------------------------ 提醒 / 系统 ------------------------------ */

export const reminderApi = {
  async page(params: Record<string, unknown>) {
    return unwrap(http.get<ApiResult<PageResult<ReminderVO>>>(`/reminders/page${qs(params)}`))
  },
  async unreadCount(): Promise<number> {
    return unwrap(http.get('/reminders/unread-count'))
  },
  async read(id: number): Promise<boolean> {
    return unwrap(http.post(`/reminders/${id}/read`))
  },
  async readAll(): Promise<number> {
    return unwrap(http.post('/reminders/read-all'))
  },
  async generate(): Promise<number> {
    return unwrap(http.post('/reminders/generate'))
  },
}

export const systemApi = {
  async health(): Promise<Record<string, unknown>> {
    return unwrap(httpRoot.get('/public/health'))
  },
}
