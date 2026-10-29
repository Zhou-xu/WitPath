# WitPath（学途知行）接口文档

本文档覆盖 **Spring Boot 业务后端**（13 个控制器）与 **FastAPI AI 服务**（4 个路由）的全部对外接口。

| 服务 | 技术栈 | 默认基址 | 说明 |
|---|---|---|---|
| 业务后端 | Spring Boot 3.5 / Java 17 | `http://127.0.0.1:8080` | `server.servlet.context-path` 为 `/`，路径即控制器上声明的原样（`/login/**`、`/api/**`、`/public/**`） |
| AI 服务 | FastAPI + LangChain / LangGraph | `http://127.0.0.1:8000` | **内部服务**，只由后端通过 `AiGateway` 调用，不直接暴露给浏览器 |
| 前端 | Vue 3 + Vite | `http://127.0.0.1:5173` | 开发态用 Vite proxy 转发 `/api`、`/login`、`/register`、`/public`、`/files` |

> 文档随代码更新；字段以 `pojo` 模块中的 DTO / VO 为准，枚举取值见 [附录 B](#附录-b-枚举字典)。

---

## 1. 通用约定

### 1.1 统一响应体

所有后端接口（含错误）返回同一结构 `Result<T>`：

```json
{ "code": 200, "msg": null, "data": {} }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | Integer | `200` 成功；`400` 参数/业务校验失败；`401` 未登录或 Token 失效；`403` 无权限；`500` 服务器内部错误 |
| `msg` | String | 失败时的错误信息，成功为 `null` |
| `data` | T | 业务数据，失败为 `null` |

### 1.2 分页

分页查询统一接收 `PageQueryDTO`（query 参数），返回 `PageResult<T>`：

**请求**

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `page` | Long | 1 | 页码，从 1 开始 |
| `size` | Long | 10 | 每页条数 |
| `keyword` | String | — | 关键字模糊匹配（按模块生效） |

**响应**

```json
{ "code": 200, "msg": null, "data": { "page": 1, "size": 10, "total": 42, "pages": 5, "records": [] } }
```

### 1.3 鉴权

- **业务接口**：请求头 `Authorization: Bearer <token>`。Token 由 `POST /login` 返回，JWT 无状态校验，用户身份取自 Token，不在请求中传 userId。
- **免鉴权白名单**（Spring Security `permitAll`）：`/login/**`、`/register/**`、`/public/**`、`/captcha/**`、`/files/**`、`/api/user/avatar/raw`（头像代理输出，`<img>` 无法携带 Token）。
- **内部回调**：`/internal/**` 不走 JWT，用 `X-WitPath-AI-Token` 请求头（或 `Authorization: Bearer <token>`）比对 `witpath.ai.callback-token`，不匹配返回 403。
- **管理端**：`/admin/**` 需 `ROLE_ADMIN`，当前无实现。
- **CORS**：允许任意来源、允许凭证、方法限 `GET/POST/PUT/DELETE/OPTIONS`。

### 1.4 时间与数值格式

| 类型 | 格式 | 示例 |
|---|---|---|
| `LocalDate` | `yyyy-MM-dd` | `2026-10-23` |
| `LocalDateTime` | `yyyy-MM-dd'T'HH:mm:ss` | `2026-10-23T19:46:41` |
| 雪花 ID（登录会话标识） | **字符串** | `"371992692395216896"` |

> 雪花 ID 为 19 位，超出 JS 安全整数范围（2^53-1），后端强制以字符串下发（`witpath.jackson.long-to-string: auto`）。前端若转成 Number 会末位舍入，导致第二步查 Redis miss。

### 1.5 AI 分析任务的异步模型

```
POST /api/ai/analyze  →  立即返回 AiTaskVO(status=0 排队中, taskNo)
                     →  经 RabbitMQ 派发 → AiGateway 调用 FastAPI /api/ai/analyze
                     →  FastAPI 每完成一个节点回调 POST /internal/ai/progress
                     →  前端轮询 GET /api/ai/tasks/{taskNo} 获取进度与结论
```

MQ 不可用时自动降级为本地线程池同步执行，接口契约不变。

---

## 2. 接口总览

### 认证与账号

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/login/LoginInitId` | 创建登录会话，返回会话标识 | 否 |
| POST | `/login/image` | 获取图形验证码 | 否 |
| POST | `/login` | 提交登录，换取 Token | 否 |
| POST | `/register` | 注册账号 | 否 |
| GET | `/api/user/profile` | 当前用户资料 | 是 |
| PUT | `/api/user/profile` | 修改资料 | 是 |
| POST | `/api/user/password` | 修改密码 | 是 |
| GET | `/api/user/avatar/presets` | 内置头像列表 | 是 |
| POST | `/api/user/avatar` | 上传图片作头像 | 是 |
| POST | `/api/user/avatar/preset` | 选用内置头像 | 是 |
| GET | `/api/user/avatar/raw` | 头像内容代理（私有桶兜底） | 否 |
| POST | `/api/user/reminders/generate` | 手动生成提醒（与 `/api/reminders/generate` 等价） | 是 |

### 个人模型配置

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/user/ai-config` | 读取个人模型配置（Key 为掩码） | 是 |
| PUT | `/api/user/ai-config` | 保存配置 | 是 |
| POST | `/api/user/ai-config/models` | 代理拉取用户端点的模型列表 | 是 |
| POST | `/api/user/ai-config/use-default` | 切回系统默认模型（配置保留） | 是 |
| DELETE | `/api/user/ai-config` | 删除个人配置 | 是 |

### 业务数据

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/dashboard/overview` | 学习概览 | 是 |
| GET | `/api/goals/page` | 目标分页 | 是 |
| GET | `/api/goals/{id}` | 目标详情 | 是 |
| POST | `/api/goals` | 新增/更新目标 | 是 |
| DELETE | `/api/goals/{id}` | 删除目标 | 是 |
| POST | `/api/goals/{id}/status` | 变更目标状态 | 是 |
| GET | `/api/tasks/page` | 任务分页 | 是 |
| GET | `/api/tasks/upcoming` | 即将到期任务 | 是 |
| POST | `/api/tasks` | 新增/更新任务 | 是 |
| DELETE | `/api/tasks/{id}` | 删除任务 | 是 |
| POST | `/api/tasks/{id}/status` | 变更任务状态 | 是 |
| GET | `/api/records/page` | 学习记录分页 | 是 |
| GET | `/api/records/recent` | 最近记录 | 是 |
| POST | `/api/records` | 新增/更新记录 | 是 |
| DELETE | `/api/records/{id}` | 删除记录 | 是 |
| GET | `/api/materials/page` | 资料分页 | 是 |
| POST | `/api/materials/upload` | 上传资料并触发向量化 | 是 |
| PUT | `/api/materials/{id}` | 修改资料信息 | 是 |
| DELETE | `/api/materials/{id}` | 删除资料（含向量清理） | 是 |
| POST | `/api/materials/{id}/ingest` | 重新解析与向量化 | 是 |
| GET | `/api/reminders/page` | 提醒分页 | 是 |
| GET | `/api/reminders/unread-count` | 未读数量 | 是 |
| POST | `/api/reminders/{id}/read` | 标记已读 | 是 |
| POST | `/api/reminders/read-all` | 全部已读 | 是 |
| POST | `/api/reminders/generate` | 生成提醒 | 是 |

### AI 能力与系统

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/api/ai/analyze` | 提交学习分析任务（异步） | 是 |
| GET | `/api/ai/tasks/{taskNo}` | 任务进度与结果 | 是 |
| GET | `/api/ai/tasks` | 任务分页 | 是 |
| GET | `/api/ai/tasks/latest` | 最近一条已完成任务 | 是 |
| POST | `/api/ai/chat` | AI 助手对话（同步） | 是 |
| GET | `/api/ai/conversations` | 会话列表 | 是 |
| GET | `/api/ai/conversations/{id}/messages` | 会话消息 | 是 |
| DELETE | `/api/ai/conversations/{id}` | 删除会话 | 是 |
| GET | `/api/ai/search` | 知识检索（走用户向量配置） | 是 |
| POST | `/internal/ai/progress` | AI 服务进度回调 | 内部令牌 |
| GET | `/public/health` | 聚合健康检查 | 否 |

---

## 3. 接口详情

### 3.1 登录 `/login`

三步式会话，前端必须按序调用。

#### 第一步：创建登录会话

```http
GET /login/LoginInitId
```

```json
{ "code": 200, "msg": null, "data": "371992692395216896" }
```

返回值为雪花 ID 字符串，Redis 有效期约 5 分钟。

#### 第二步：获取图形验证码

```http
POST /login/image
userId: 371992692395216896
```

会话标识通过 **请求头 `userId`** 传递。

```json
{ "code": 200, "msg": null,
  "data": { "captchaKey": "371993338284478464", "captchaImage": "data:image/png;base64,iVBORw0KGgo..." } }
```

`captchaKey` 即会话标识，图片为 232×76 的 data URI（前端展示框的 2 倍，兼顾高分屏）。答案不区分大小写，5 分钟内有效；会话不存在返回 `400 登录会话不存在或已过期，请刷新页面重试`。

#### 第三步：提交登录

```http
POST /login
Content-Type: application/json
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `loginInitId` | String | 是 | 第一步返回的会话标识 |
| `username` | String | 是 | 非空 |
| `password` | String | 是 | 非空 |
| `captchaAnswer` | String | 是 | 验证码答案 |

```json
{
  "code": 200, "msg": null,
  "data": { "token": "eyJhbGciOi...", "userId": 2, "username": "demo",
            "nickname": "知行同学", "avatar": "https://...", "role": 1, "expire": 604800000 }
}
```

### 3.2 注册 `/register`

```http
POST /register
```

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| `loginInitId` | String | 是 | 同登录第一步 |
| `username` | String | 是 | 3~30 位，仅字母/数字/下划线 |
| `password` | String | 是 | 6~32 位 |
| `captchaAnswer` | String | 是 | |
| `nickname` | String | 否 | ≤50 字符 |
| `email` | String | 否 | 邮箱格式或空串 |
| `studyStage` / `studyGoal` / `preferredTags` | String | 否 | 注册时可选填 |

返回 `UserVO`（见附录 A）。

### 3.3 用户资料 `/api/user`

#### 读取 / 修改资料

```http
GET  /api/user/profile
PUT  /api/user/profile     body: UserVO（仅 nickname/email/phone/avatarUrl/studyStage/studyGoal/preferredTags 生效）
```

```json
{
  "code": 200, "msg": null,
  "data": {
    "id": 2, "username": "demo", "nickname": "知行同学",
    "avatarUrl": "https://witpath-avatar.oss-cn-shanghai.aliyuncs.com/avatars/u2/preset-peach-wave-898cac87.svg",
    "email": "demo@witpath.dev", "phone": "", "role": 1, "roleName": "普通用户",
    "status": 1, "studyStage": "本科", "studyGoal": "三个月内拿下考研数学基础阶段",
    "preferredTags": "考研数学,英语,编程",
    "lastLoginAt": "2026-10-23T19:46:41", "createdAt": "2026-10-20T14:18:58"
  }
}
```

#### 修改密码

```http
POST /api/user/password
{ "oldPassword": "witpath123", "newPassword": "newpass123" }
```

`oldPassword` 非空（不正确返回 `400 原密码不正确`）；`newPassword` 6~32 位。成功后当前 Token 依然有效。返回 `true`。

#### 头像

```http
GET  /api/user/avatar/presets            → [ { id, name, svg } ]
POST /api/user/avatar                    multipart: file
POST /api/user/avatar/preset             { "presetId": "peach-wave" }
GET  /api/user/avatar/raw?key=avatars/u2/xxx.svg     → 图片二进制（免鉴权）
```

- 上传与内置头像均由后端写入阿里云 OSS（公共读桶 `witpath-avatar`），返回 OSS 永久直链并同步更新 `avatarUrl`。
- 私有桶场景下 `<img>` 改用 `/api/user/avatar/raw?key=...`，该接口仅允许 `avatars/` 前缀，响应带 30 天强缓存与 `Content-Security-Policy: default-src 'none'`（SVG 防脚本）。
- 更换头像后旧对象自动清理（非本服务生成的旧图跳过）。

### 3.4 个人模型配置 `/api/user/ai-config`

用户可自带大模型与向量模型；**API Key 全程只在请求体中出现**，落库为 AES-GCM 密文，读取永远返回掩码。

#### 读取配置

```http
GET /api/user/ai-config
```

```json
{
  "code": 200, "msg": null,
  "data": {
    "configured": true, "provider": "openai-compatible",
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "model": "qwen-plus", "apiKeyMasked": "sk-ws-********T6YM",
    "embeddingProvider": null, "embeddingModel": null,
    "embeddingBaseUrl": null, "embeddingApiKeyMasked": null,
    "embeddingOverridden": false, "useDefaultApi": false,
    "updatedAt": "2026-10-23T19:46:44"
  }
}
```

未配置时 `configured=false`，其余为 `null`。

#### 保存配置

```http
PUT /api/user/ai-config
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `baseUrl` | String | 是 | OpenAI 兼容端点，如 `https://.../v1` |
| `model` | String | 是 | 必须先经「获取模型」选定；未选返回 400 |
| `apiKey` | String | 首次必填 | 留空表示**不修改**已保存的 Key |
| `provider` | String | 否 | 默认 `openai-compatible` |
| `embeddingProvider` / `embeddingModel` / `embeddingBaseUrl` / `embeddingApiKey` | String | 否 | 向量模型（高级配置），留空表示不覆盖系统默认 |
| `useDefaultApi` | Boolean | 否 | 传 `true` 表示保存但仍走系统默认 |

保存成功后立即重建 Redis 缓存（`witpath:ai:config:{userId}`，TTL 300s），随后的 AI 调用不会读到旧值。

#### 拉取模型列表（后端代理）

```http
POST /api/user/ai-config/models
{ "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1", "apiKey": "sk-xxx" }
```

后端依次尝试 `{base}/models` 与 `{base}/v1/models`（连接 5s / 读取 12s），最多返回 200 个：

```json
{ "code": 200, "msg": null, "data": [ { "id": "qwen-plus", "name": "qwen-plus" } ] }
```

错误提示按状态码区分：401 → Key 或权限问题；404 → 地址不是 OpenAI 兼容端点。

#### 切回系统默认 / 删除

```http
POST   /api/user/ai-config/use-default   → 配置行保留（可改回），useDefaultApi=true
DELETE /api/user/ai-config               → 物理删除，恢复系统默认
```

### 3.5 学习概览 `/api/dashboard`

```http
GET /api/dashboard/overview?days=14
```

`days` 默认 14，用于趋势与统计的回溯窗口。

```json
{
  "code": 200, "msg": null,
  "data": {
    "overview": { "totalMinutes": 0, "last7DaysMinutes": 0, "todayMinutes": 0, "streakDays": 0,
                  "recordCount": 0, "activeGoalCount": 0, "todoTaskCount": 0,
                  "overdueTaskCount": 0, "doneTaskCount": 0, "avgFocus": 0.0, "avgGoalProgress": 0 },
    "trend": [ { "date": "2026-10-10", "minutes": 0, "records": 0, "focus": 0.0 } ],
    "subjects": [ { "subject": "数学", "minutes": 0, "records": 0, "ratio": 0.0 } ],
    "focusTrend": [], "goals": [], "upcomingTasks": [], "recentRecords": [],
    "latestInsight": null, "unreadReminders": 0
  }
}
```

### 3.6 学习目标 `/api/goals`

```http
GET    /api/goals/page?page=1&size=10&keyword=&status=1
GET    /api/goals/{id}
POST   /api/goals
DELETE /api/goals/{id}
POST   /api/goals/{id}/status?status=2
```

`POST /api/goals` 请求体 `GoalSaveDTO`：

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | Long | 否 | — | 传 id 为更新，不传为新增 |
| `title` | String | 是 | — | 目标标题 |
| `description` / `subject` | String | 否 | — | |
| `priority` | Integer | 否 | 2 | 1 高 / 2 中 / 3 低 |
| `status` | Integer | 否 | 1 | 见 [GoalStatus](#附录-b-枚举字典) |
| `startDate` / `endDate` | LocalDate | 否 | — | `yyyy-MM-dd` |
| `targetHours` | BigDecimal | 否 | 0 | 目标时长 |

返回 `GoalVO`：除上述字段外，还有 `statusText`、`actualHours`、`taskTotal`、`taskDone`、`progress`、`daysLeft`、`overdue` 等派生统计。

### 3.7 学习任务 `/api/tasks`

```http
GET    /api/tasks/page?page=1&size=10&status=1&goalId=2
GET    /api/tasks/upcoming?limit=6
POST   /api/tasks
DELETE /api/tasks/{id}
POST   /api/tasks/{id}/status?status=3
```

`TaskSaveDTO`：`id`（传则更新）、`goalId`、`title`、`description`、`status`(默认1)、`priority`(默认2)、`planDate`、`dueDate`、`estimatedMinutes`(默认0)、`sortOrder`(默认0)。

`TaskVO` 附带 `goalTitle`、`statusText`、`actualMinutes`、`completedAt`、`overdue`、`daysLeft`。

### 3.8 学习记录 `/api/records`

```http
GET    /api/records/page?page=1&size=10&from=2026-10-01&to=2026-10-23&subject=数学
GET    /api/records/recent?limit=8
POST   /api/records
DELETE /api/records/{id}
```

`from` / `to` 为 `yyyy-MM-dd`。`RecordSaveDTO`：`id`、`goalId`、`taskId`、`title`、`content`、`subject`、`studyDate`、`startAt`、`endAt`、`durationMinutes`、`focusScore`(1~5)、`outcome`、`tags`。

`RecordVO` 附带 `goalTitle`、`taskTitle`、`durationText`。

### 3.9 学习资料 `/api/materials`

#### 上传

```http
POST /api/materials/upload
Content-Type: multipart/form-data

file=<二进制>&subject=数学&description=第一章讲义
```

- 大小上限 20 MB，扩展名受 `witpath.storage.allowed-extensions` 约束，超限或类型不支持返回 400
- 存储按 `witpath.storage.type`：`oss` 写入 OSS 私有桶 `witpath-document`；`local` 落本地目录并通过 `/files/**` 静态映射
- 落库后**立即异步触发**解析与向量化（`parseStatus` / `vectorStatus` 初始为 0）

#### 其它

```http
GET    /api/materials/page?page=1&size=10&subject=数学
PUT    /api/materials/{id}          { "name": "", "subject": "", "description": "" }
DELETE /api/materials/{id}          # 同步清理对象存储与向量库
POST   /api/materials/{id}/ingest   # 重新解析 + 向量化
```

`MaterialVO` 含 `parseStatus` / `vectorStatus`（0 未处理 / 1 处理中 / 2 已完成 / 3 失败）、`chunkCount`、`errorMessage`、`url`（下载直链或 `/files/...`）。

### 3.10 学习提醒 `/api/reminders`

```http
GET    /api/reminders/page?page=1&size=10&status=0
GET    /api/reminders/unread-count
POST   /api/reminders/{id}/read
POST   /api/reminders/read-all
POST   /api/reminders/generate
```

`generate` 依据当前目标、任务与记录生成提醒，返回新增条数（无新增返回 0）。`ReminderVO.level`：1 提示 / 2 警告 / 3 严重。

### 3.11 AI 能力 `/api/ai`

#### 提交分析任务

```http
POST /api/ai/analyze
{ "type": "STUDY_ANALYSIS", "goalId": null, "rangeDays": 14, "question": "我该怎么调整计划？" }
```

`type` 取值见 [AiTaskType](#附录-b-枚举字典)；`question` ≤1000 字。立即返回 `AiTaskVO`（`status=0` 排队中），前端用 `taskNo` 轮询。

#### 查询任务

```http
GET /api/ai/tasks/{taskNo}
GET /api/ai/tasks?page=1&size=10&type=STUDY_ANALYSIS
GET /api/ai/tasks/latest?type=STUDY_ANALYSIS
```

```json
{
  "code": 200, "msg": null,
  "data": {
    "id": 3, "taskNo": "577198be-2397-4a24-be04-97a03f8d3b46", "type": "STUDY_ANALYSIS",
    "title": "学习态势分析", "status": 3, "statusText": "失败",
    "stage": "queued", "stageText": "已排队", "stageMessage": "执行失败",
    "progress": 0, "summary": null, "suggestion": null, "result": null,
    "model": null, "tokensUsed": 0, "latencyMs": 383,
    "errorMessage": "AI 服务连接失败...", "startedAt": null, "finishedAt": null
  }
}
```

`progress` 为 0~100，`result` 为 AI 服务返回的完整结论（JsonNode）。

#### AI 助手对话

```http
POST /api/ai/chat
{ "conversationId": 3, "message": "二叉树有哪些遍历方式？", "withContext": true }
```

`message` 必填且 ≤4000 字；`conversationId` 为空则新建会话；`withContext=true` 时附带学习上下文与知识检索。返回 `ChatMessageVO`（含 `referenced` 引用片段、`tokensUsed`）。

#### 会话管理

```http
GET    /api/ai/conversations                 → [ { id, title, messageCount, lastMessageAt, createdAt } ]
GET    /api/ai/conversations/{id}/messages   → [ ChatMessageVO ]
DELETE /api/ai/conversations/{id}            → true
```

#### 知识检索

```http
GET /api/ai/search?q=二叉树&topK=5
```

返回 AI 服务原始结构 `{ query, top_k, backend, results: [{ source, snippet, score }] }`。该接口走 **POST + body** 转发，以便携带用户个人向量模型配置（Key 不进 URL 与访问日志）。

### 3.12 内部进度回调 `/internal/ai/progress`

```http
POST /internal/ai/progress
X-WitPath-AI-Token: <witpath.ai.callback-token>

{ "taskNo": "577198be...", "stage": "analysis", "stageMessage": "已完成学习态势分析",
  "progress": 45, "tokensUsed": 120, "model": "qwen-plus" }
```

令牌不匹配返回 `403 回调令牌无效`。此接口由 FastAPI 在 LangGraph 各节点调用，前端无需关心。

### 3.13 健康检查 `/public/health`

```http
GET /public/health
```

```json
{
  "code": 200, "msg": null,
  "data": { "service": "server", "backend": "UP", "storage": "oss", "mq": "UP",
            "ai": "UP", "aiDetail": { "status": "ok", "engine": "llm", "model": "...", "vector": {...} } }
}
```

`mq` 在 MQ 不可用时为 `DEGRADED`；`ai` 不可用时为 `DOWN` 并附带 `aiError`。

---

## 4. AI 服务接口（FastAPI，内部）

基址 `http://127.0.0.1:8000`，由后端 `AiGateway` 调用。字段为 camelCase（`CamelModel` 自动与 snake_case 互转）。

| 方法 | 路径 | 调用方 | 说明 |
|---|---|---|---|
| GET | `/health` | `/public/health` | 服务、Python 版本、模型引擎、向量库、checkpoint、OSS 状态 |
| POST | `/api/ai/analyze` | 分析任务执行 | LangGraph 多 Agent 工作流 |
| POST | `/api/ai/chat` | `POST /api/ai/chat` | 多轮对话 |
| POST | `/api/ai/documents/ingest` | 资料向量化 | 解析 → 分块 → 向量化 → 写入检索库 |
| DELETE | `/api/ai/documents/{material_id}` | 删除资料 | 按 `[materialId]` 前缀清理向量 |
| GET | `/api/ai/documents/search` | — | 语义检索（服务端默认向量模型） |
| POST | `/api/ai/documents/search` | `GET /api/ai/search` | 语义检索（可带用户向量模型配置） |

### 4.1 `POST /api/ai/analyze`

请求体 `AiAnalyzeRequest`：`user_id`、`task_no`、`task_type`、`question`、`callback_url`、`callback_token`、`profile`、`summary`(range_days)、`goals[]`、`tasks[]`、`records[]`、`material_snippets[]`、`user_model_config`。

响应 `AiAnalyzeResponse`：`task_no`、`type`、`status`、`model`、`tokens_used`、`latency_ms`、`summary`、`suggestion`、`risks[]`、`plan[]`（`{title, detail, due_date, estimated_minutes, priority}`）、`stages[]`、`error`。

### 4.2 `POST /api/ai/chat`

请求体 `ChatRequest`：`user_id`、`nickname`、`message`、`history[]`（`{role, content}`）、`use_knowledge`、`context`、`user_model_config`。

响应 `ChatResponse`：`reply`、`tokens_used`、`model`、`referenced[]`（`{source, snippet, score}`）、`error`。

### 4.3 `POST /api/ai/documents/ingest`

请求体 `DocIngestRequest`：`user_id`、`material_id`、`file_name`、`file_type`、`storage_type`、`local_path`、`oss_bucket`、`oss_object_key`、`subject`、`user_model_config`。

响应 `DocIngestResponse`：`material_id`、`status`、`chunk_count`、`vector_backend`、`error`。

### 4.4 用户模型配置契约 `UserModelConfig`

| 字段 | 说明 |
|---|---|
| `llmProvider` / `llmApiKey` / `llmBaseUrl` / `llmModel` | 大模型；`has_llm` = 三者齐全 |
| `embeddingProvider` / `embeddingApiKey` / `embeddingBaseUrl` / `embeddingModel` | 向量模型；`has_embedding` = Key 与模型名齐全 |

> 字段名不能叫 `model_config` —— 那是 pydantic 的保留属性名。任何字段留空即回落服务端 `.env` 默认配置，只配一半也能正常工作。

**当前生效范围**：`chat` 的大模型、`ingest` 与 `POST /documents/search` 的向量模型已生效；`/api/ai/analyze` 工作流内部节点与对话中的知识检索暂未透传用户配置（走系统默认），详见代码 `app/agents/nodes.py`。

---

## 附录 A：数据字典

### UserVO

`id`、`username`、`nickname`、`avatarUrl`、`email`、`phone`、`role`、`roleName`、`status`、`studyStage`、`studyGoal`、`preferredTags`、`lastLoginAt`、`createdAt`

### GoalVO

`id`、`title`、`description`、`subject`、`priority`、`status`、`statusText`、`startDate`、`endDate`、`targetHours`、`actualHours`、`taskTotal`、`taskDone`、`progress`、`daysLeft`、`overdue`、`createdAt`

### TaskVO

`id`、`goalId`、`goalTitle`、`title`、`description`、`status`、`statusText`、`priority`、`planDate`、`dueDate`、`estimatedMinutes`、`actualMinutes`、`completedAt`、`sortOrder`、`overdue`、`daysLeft`、`createdAt`

### RecordVO

`id`、`goalId`、`goalTitle`、`taskId`、`taskTitle`、`title`、`content`、`subject`、`studyDate`、`startAt`、`endAt`、`durationMinutes`、`durationText`、`focusScore`、`outcome`、`tags`、`createdAt`

### MaterialVO

`id`、`name`、`originalName`、`fileType`、`fileSize`、`fileSizeText`、`storageType`、`storageTypeText`、`subject`、`description`、`parseStatus`、`parseStatusText`、`vectorStatus`、`vectorStatusText`、`chunkCount`、`errorMessage`、`url`、`createdAt`

### ReminderVO

`id`、`goalId`、`taskId`、`type`、`level`、`title`、`content`、`source`、`status`、`statusText`、`triggerTime`、`readAt`、`createdAt`

### AiTaskVO

`id`、`taskNo`、`type`、`title`、`question`、`status`、`statusText`、`stage`、`stageText`、`stageMessage`、`progress`、`summary`、`suggestion`、`result`、`model`、`tokensUsed`、`latencyMs`、`errorMessage`、`startedAt`、`finishedAt`、`createdAt`

### ChatMessageVO / ConversationVO

`ChatMessageVO`：`id`、`conversationId`、`role`、`content`、`referenced`、`tokensUsed`、`createdAt`
`ConversationVO`：`id`、`title`、`messageCount`、`lastMessageAt`、`createdAt`

### DashboardVO

`overview{ totalMinutes, last7DaysMinutes, todayMinutes, streakDays, recordCount, activeGoalCount, todoTaskCount, overdueTaskCount, doneTaskCount, avgFocus, avgGoalProgress }`、`trend[{date, minutes, records, focus}]`、`subjects[{subject, minutes, records, ratio}]`、`focusTrend`、`goals[{id, title, progress, targetHours, actualHours, status, endDate}]`、`upcomingTasks[TaskVO]`、`recentRecords[RecordVO]`、`latestInsight{taskId, taskNo, type, summary, suggestion, createdAt}`、`unreadReminders`

---

## 附录 B：枚举字典

| 枚举 | 取值 |
|---|---|
| GoalStatus（目标） | 1 进行中 / 2 已完成 / 3 已暂停 / 4 已归档 |
| TaskStatus（任务） | 1 待办 / 2 进行中 / 3 已完成 / 4 已延期 |
| ReminderStatus（提醒） | 0 未读 / 1 已读 / 2 已处理 |
| ProcessStatus（解析/向量化） | 0 未处理 / 1 处理中 / 2 已完成 / 3 失败 |
| AiTaskStatus（AI 任务） | 0 排队中 / 1 执行中 / 2 已完成 / 3 失败 / 4 已取消 |
| AiTaskType（AI 任务类型） | `STUDY_ANALYSIS` / `STUDY_PLAN` / `SUMMARY` / `CHAT` |
| AiStage（AI 任务阶段） | `queued` / `data_collect` / `analysis` / `planning` / `retrieval` / `advice` / `summary` |
| UserRole | 1 普通用户 / 2 管理员 |

---

## 附录 C：本地联调

```bash
# 1. 启动依赖（MySQL / Redis / RabbitMQ / Milvus）
# 2. 启动 AI 服务
cd Agent && uvicorn app.main:app --host 0.0.0.0 --port 8000

# 3. 启动后端（需先停掉旧进程，避免覆盖运行中的 jar）
cd backend/WitPath && mvn -o -DskipTests package
java -jar server/target/server-0.0.1-SNAPSHOT.jar --server.port=8080

# 4. 启动前端
cd frontend/WitPath && npm run dev

# 5. 冒烟测试
python tools/smoke_test.py --username demo --password witpath123
python tools/ai_config_check.py
```

演示账号：`demo / witpath123`。健康检查：`curl http://127.0.0.1:8080/public/health`。
