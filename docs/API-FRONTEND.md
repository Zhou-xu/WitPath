# WitPath 前后端接口对接文档

> 面向**前端调用方**：讲清楚前端每一个 API 方法打向哪个后端接口、谁在调、什么时候调、超时多少、出错怎么表现。
>
> 配套文档：`docs/API.md`（后端 53 个接口 + AI 服务 7 个接口的完整规格，含参数表与响应示例）。
> 本文只做「前端方法 ↔ 后端接口」的对接映射，参数细节以 `API.md` 为准。

| 项 | 值 |
|---|---|
| 前端 | Vue 3 + TypeScript，dev server `http://127.0.0.1:5173` |
| 后端 | Spring Boot 3.5，`http://127.0.0.1:8080`，`context-path` 为 `/`（**路径即控制器原样，没有额外前缀**） |
| AI 服务 | FastAPI，`:8000`，**前端不直接访问**，一律经 Spring Boot 转发 |
| API 层源码 | `frontend/WitPath/src/api/request.ts` + `frontend/WitPath/src/api/index.ts` |

---

## 1. 前端 API 层架构

```
视图 / 组件 / store
        │  authApi.xxx()  goalApi.xxx()  aiApi.xxx() …
        ▼
api/index.ts（11 个 API 分组，唯一出口）
        │  unwrap() 解包 { code, msg, data }
        ▼
api/request.ts（两套 axios 实例 + 拦截器）
        │  Authorization: Bearer <token>
        ▼
Vite proxy（dev） / Nginx 反代（prod）→ Spring Boot
```

### 1.1 两套 axios 实例

后端 `context-path` 是 `/`，而接口分布在不同前缀下，因此前端建了两个实例：

| 实例 | baseURL | 用途 | 覆盖接口 |
|---|---|---|---|
| `http` | `/api`（可用 `VITE_API_BASE` 覆盖） | 登录后的一切业务接口 | `/api/user`、`/api/goals`、`/api/tasks`、`/api/records`、`/api/materials`、`/api/ai`、`/api/reminders`、`/api/dashboard` |
| `httpRoot` | `/` | 认证与公开接口 | `/login/**`、`/register/**`、`/public/**` |

> 之所以需要两套：`/login`、`/register` 不在 `/api` 下，用同一个 `/api` 实例会拼成错误的 `/api/login`。

### 1.2 请求链路

`api/request.ts`：

1. **Token**：`localStorage` 键名 `witpath_token`；`getToken/setToken/clearToken` 三个函数统一管理
2. **请求拦截器**：两个实例都挂 `attachToken`，有 token 就写 `Authorization: Bearer <token>`
3. **响应拦截器 `onError`**：
   - 取 `error.response.data.msg` 作为错误信息（后端 `Result.msg`），取不到再用 axios 原生 `message`
   - **401 专项处理**：调 `userStore.logout(false)` 清本地态，然后跳转 `/login?redirect=<当前路径+查询串>`，登录后能回到原页面
   - 统一 `Promise.reject(new Error(msg))`，页面 `catch` 到的就是可直接 toast 的中文文案
4. **`unwrap<T>()`**：`code !== 200` 直接 throw；否则返回 `data`。所以业务代码拿到的永远是 `data`，不用层层 `.data.data`
5. **`qs()`**：把对象转成查询串，**自动跳过 `undefined` / `null` / 空字符串**，避免把 `?status=&keyword=` 这种空参发给后端

### 1.3 超时策略

默认 20s，四个重操作单独放宽：

| 场景 | 超时 | 原因 |
|---|---|---|
| 常规接口 | 20s | — |
| `avatarApi.upload` | 60s | 图片需转存阿里云 OSS |
| `materialApi.upload` | 120s | 大文件 + 后端可能触发解析 |
| `aiConfigApi.models` | 30s | 后端代理访问用户自定义端点拉模型列表，网络不可控 |

### 1.4 开发环境代理

`vite.config.ts` 转发 5 个前缀到 `http://127.0.0.1:8080`（可用环境变量 `WITPATH_BACKEND` 覆盖）：

```
/api  /login  /register  /public  /files
```

`/login` 与 `/register` 同时是**前端路由**和**后端接口**，因此加了 `spaBypass`：浏览器 GET 且 `Accept: text/html`（刷新/直达）时回落到 `/index.html` 交给 SPA 渲染，axios 的 JSON 请求照常转发。这样刷新登录页不会被后端返回 405。

生产由 Nginx / 网关把 `/api` 反代到 Spring Boot，同源部署无跨域。

---

## 2. 全量映射表：前端方法 → 后端接口

「鉴权」列：✅ 需 Bearer Token；⬜ 白名单匿名可访问。

### 2.1 认证 `authApi`

| 前端方法 | HTTP | 路径 | 鉴权 | 返回 | 调用位置 |
|---|---|---|---|---|---|
| `initSession()` | GET | `/login/LoginInitId` | ⬜ | `string`（雪花 ID 字符串） | LoginView、RegisterView |
| `captcha(loginInitId)` | POST | `/login/image` | ⬜ | `CaptchaVO` | LoginView、RegisterView |
| `login(payload)` | POST | `/login` | ⬜ | `LoginVO` | LoginView |
| `register(payload)` | POST | `/register` | ⬜ | `UserVO` | RegisterView |
| `profile()` | GET | `/api/user/profile` | ✅ | `UserVO` | `stores/user.ts#loadProfile` |
| `updateProfile(payload)` | PUT | `/api/user/profile` | ✅ | `UserVO` | SettingsView |
| `changePassword(payload)` | POST | `/api/user/password` | ✅ | `boolean` | `PasswordChangeModal.vue` |

> `captcha()` 的会话 ID 走 **请求头 `userId`**，不是 body —— 后端 `LoginController` 从 header 读取。

### 2.2 头像 `avatarApi`

| 前端方法 | HTTP | 路径 | 鉴权 | 返回 | 调用位置 |
|---|---|---|---|---|---|
| `presets()` | GET | `/api/user/avatar/presets` | ✅ | `AvatarPresetVO[]` | AvatarPickerModal |
| `upload(file)` | POST | `/api/user/avatar` | ✅ | `UserVO` | AvatarPickerModal |
| `applyPreset(presetId)` | POST | `/api/user/avatar/preset` | ✅ | `UserVO` | AvatarPickerModal |

上传用 `FormData` 且显式声明 `multipart/form-data`。头像最终落在阿里云 OSS 公共读桶 `witpath-avatar`；后端优先返回永久直链，若直链不可达则改为返回匿名可读的 `/api/user/avatar/raw` 代理地址（该路径在 Security 白名单内，`<img>` 无需带 Token）。前端不做地址判断，直接用 `UserVO.avatarUrl` 作为 `<img src>`，加载失败时 `UserAvatar` 回退为昵称首字。

### 2.3 个人大模型配置 `aiConfigApi`

| 前端方法 | HTTP | 路径 | 鉴权 | 返回 | 调用位置 |
|---|---|---|---|---|---|
| `get()` | GET | `/api/user/ai-config` | ✅ | `UserAiConfigVO` | SettingsView、AiConfigModal |
| `save(payload)` | PUT | `/api/user/ai-config` | ✅ | `UserAiConfigVO` | AiConfigModal |
| `useDefault()` | POST | `/api/user/ai-config/use-default` | ✅ | `UserAiConfigVO` | AiConfigModal |
| `remove()` | DELETE | `/api/user/ai-config` | ✅ | `boolean` | AiConfigModal |
| `models({baseUrl, apiKey})` | POST | `/api/user/ai-config/models` | ✅ | `ModelOptionVO[]` | AiConfigModal |

要点：
- **无密码确认**（已移除解锁链路），打开弹窗即表单
- 后端只返回 **API Key 掩码**（`sk-ws-****T6YM`），明文永不出后端；`save` 时 Key 留空表示「不修改已保存的值」
- 模型列表由**后端代理**拉取，用户 Key 不参与浏览器侧跨域请求
- `useDefaultApi=true` 时个人配置保留但不下发，AI 走系统默认

### 2.4 概览 `dashboardApi`

| 前端方法 | HTTP | 路径 | 鉴权 | 返回 | 调用位置 |
|---|---|---|---|---|---|
| `overview(days = 14)` | GET | `/api/dashboard/overview?days=14` | ✅ | `DashboardVO` | DashboardView |

### 2.5 目标 `goalApi` / 任务 `taskApi` / 记录 `recordApi`

| 前端方法 | HTTP | 路径 | 鉴权 | 返回 | 调用位置 |
|---|---|---|---|---|---|
| `goalApi.page(params)` | GET | `/api/goals/page` | ✅ | `PageResult<GoalVO>` | GoalsView、RecordsView、AssistantView |
| `goalApi.save(payload)` | POST | `/api/goals` | ✅ | `GoalVO` | GoalsView |
| `goalApi.remove(id)` | DELETE | `/api/goals/{id}` | ✅ | `boolean` | GoalsView |
| `goalApi.changeStatus(id, status)` | POST | `/api/goals/{id}/status?status=` | ✅ | `GoalVO` | GoalsView |
| `taskApi.page(params)` | GET | `/api/tasks/page` | ✅ | `PageResult<TaskVO>` | GoalsView |
| `taskApi.save(payload)` | POST | `/api/tasks` | ✅ | `TaskVO` | GoalsView |
| `taskApi.remove(id)` | DELETE | `/api/tasks/{id}` | ✅ | `boolean` | GoalsView |
| `taskApi.changeStatus(id, status)` | POST | `/api/tasks/{id}/status?status=` | ✅ | `TaskVO` | GoalsView |
| `recordApi.page(params)` | GET | `/api/records/page` | ✅ | `PageResult<RecordVO>` | RecordsView |
| `recordApi.save(payload)` | POST | `/api/records` | ✅ | `RecordVO` | RecordsView |
| `recordApi.remove(id)` | DELETE | `/api/records/{id}` | ✅ | `boolean` | RecordsView |

> 任务与目标共用「目标」页：GoalsView 同时调 `goalApi` 与 `taskApi`，任务在目标抽屉内增删改。

### 2.6 资料 `materialApi`

| 前端方法 | HTTP | 路径 | 鉴权 | 返回 | 调用位置 |
|---|---|---|---|---|---|
| `page(params)` | GET | `/api/materials/page` | ✅ | `PageResult<MaterialVO>` | MaterialsView |
| `upload(file, subject?, description?)` | POST | `/api/materials/upload` | ✅ | `MaterialVO` | MaterialsView |
| `remove(id)` | DELETE | `/api/materials/{id}` | ✅ | `boolean` | MaterialsView |
| `ingest(id)` | POST | `/api/materials/{id}/ingest` | ✅ | `boolean` | MaterialsView |

上传为 `multipart/form-data`，`subject` / `description` 作为同表单字段提交（不是 query）。

### 2.7 AI `aiApi`

| 前端方法 | HTTP | 路径 | 鉴权 | 返回 | 调用位置 |
|---|---|---|---|---|---|
| `analyze(payload)` | POST | `/api/ai/analyze` | ✅ | `AiTaskVO` | DashboardView、AssistantView |
| `taskDetail(taskNo)` | GET | `/api/ai/tasks/{taskNo}` | ✅ | `AiTaskVO` | AssistantView |
| `latest(type?)` | GET | `/api/ai/tasks/latest` | ✅ | `AiTaskVO \| null` | AssistantView |
| `chat(payload)` | POST | `/api/ai/chat` | ✅ | `ChatMessageVO` | AssistantView |
| `conversations()` | GET | `/api/ai/conversations` | ✅ | `ConversationVO[]` | AssistantView |
| `messages(conversationId)` | GET | `/api/ai/conversations/{id}/messages` | ✅ | `ChatMessageVO[]` | AssistantView |
| `removeConversation(id)` | DELETE | `/api/ai/conversations/{id}` | ✅ | `boolean` | AssistantView |

### 2.8 提醒与系统

| 前端方法 | HTTP | 路径 | 鉴权 | 返回 | 调用位置 |
|---|---|---|---|---|---|
| `reminderApi.page(params)` | GET | `/api/reminders/page` | ✅ | `PageResult<ReminderVO>` | SettingsView |
| `reminderApi.unreadCount()` | GET | `/api/reminders/unread-count` | ✅ | `number` | `AppTopbar.vue`（挂载时） |
| `reminderApi.generate()` | POST | `/api/reminders/generate` | ✅ | `number`（新增条数） | SettingsView |
| `systemApi.health()` | GET | `/public/health` | ⬜ | `Record<string, unknown>` | SettingsView「服务状态」 |

---

## 3. 页面 → 接口调用矩阵

| 路由 | 视图 / 组件 | 调用的 API 方法 |
|---|---|---|
| `/login` | LoginView | `initSession` → `captcha` → `login` |
| `/register` | RegisterView | `initSession` → `captcha` → `register` |
| —（全局） | `stores/user.ts` | `profile`（`loadProfile`，已加载则不重复请求） |
| —（全局） | `AppTopbar.vue` | `reminderApi.unreadCount`（`onMounted`） |
| `/app/dashboard` | DashboardView | `dashboardApi.overview`、`aiApi.analyze` |
| `/app/goals` | GoalsView | `goalApi.*`、`taskApi.*` |
| `/app/records` | RecordsView | `recordApi.*`、`goalApi.page`（下拉选目标） |
| `/app/materials` | MaterialsView | `materialApi.*` |
| `/app/assistant` | AssistantView | `aiApi.analyze`、`taskDetail`、`latest`、`chat`、`conversations`、`messages`、`removeConversation`、`goalApi.page` |
| `/app/settings` | SettingsView | `authApi.updateProfile`、`systemApi.health`、`reminderApi.page`、`reminderApi.generate`、`aiConfigApi.get` |
| —（弹窗） | `PasswordChangeModal.vue` | `authApi.changePassword` |
| —（弹窗） | `AvatarPickerModal.vue` | `avatarApi.presets` / `upload` / `applyPreset` |
| —（弹窗） | `AiConfigModal.vue` | `aiConfigApi.get` / `save` / `models` / `useDefault` / `remove` |

---

## 4. 关键调用时序

### 4.1 登录三段式（验证码会话）

```
GET  /login/LoginInitId          →  loginInitId（字符串，雪花 ID）
POST /login/image   header: userId=loginInitId  →  { captchaKey, captchaImage(base64) }
POST /login         body: { loginInitId, username, password, captchaAnswer }  →  LoginVO
```

拿到 `LoginVO` 后：`userStore.applyLogin()` 写 `localStorage.witpath_token`，随后 `loadProfile(true)` 拉 `UserVO`。
`loginInitId` 与验证码答案在 Redis 中 TTL 5 分钟，超时需重新走第一步。

### 4.2 AI 分析：异步任务 + 轮询

```
POST /api/ai/analyze  →  AiTaskVO(status=0/1, taskNo)
        │
        └─ startPolling(taskNo)：每 1500ms
                GET /api/ai/tasks/{taskNo}
                status >= 2 → stopPolling + toast（2 成功 / 3 失败）
```

- DashboardView 提交 `type: 'STUDY_ANALYSIS'` 后**不轮询**，直接 `router.push` 到 `/app/assistant?task=<taskNo>`
- AssistantView 通过 `watch` 监听路由上的 `task` 参数，命中后调 `taskDetail` 接管展示；无参数时调 `latest(type)` 恢复最近一次结果
- 轮询期间任何异常都 `stopPolling`，避免错误弹窗刷屏

### 4.3 资料上传 → 向量入库（两步）

```
POST /api/materials/upload (multipart)  →  MaterialVO(parseStatus, vectorStatus)
POST /api/materials/{id}/ingest         →  boolean（触发 AI 服务切片 + 向量化）
```

入库是**独立动作**，需用户点「入库」或由后端在上传后自动触发；前端按 `vectorStatus` 展示状态标签。

### 4.4 个人模型配置

```
GET /api/user/ai-config                  →  掩码配置（configured=false 表示未配置）
POST /api/user/ai-config/models          →  模型下拉选项（后端代理用户端点）
PUT /api/user/ai-config                  →  保存（未选模型不允许提交）
POST /api/user/ai-config/use-default     →  切回系统默认（配置保留）
DELETE /api/user/ai-config               →  删除
```

---

## 5. 类型契约

### 5.1 一致性校验

已用脚本逐字段比对后端 `pojo/.../vo/*.java` 与前端 `api/index.ts` 的 interface，**14 个 VO 全部对齐、零差异**：

`UserVO` `LoginVO` `CaptchaVO` `AvatarPresetVO` `GoalVO` `TaskVO` `RecordVO` `MaterialVO` `AiTaskVO` `ChatMessageVO` `ConversationVO` `ReminderVO` `UserAiConfigVO` `ModelOptionVO`

新增/修改 VO 时，请同步两侧并保留这份对齐（后端 camelCase 返回，前端 interface 直接同名映射，不需要转换层）。

### 5.2 三个易错点

1. **头像字段两套名字**：登录返回 `LoginVO.avatar`，资料返回 `UserVO.avatarUrl`。这是后端自身差异；`UserAvatar` 组件只认 `url` prop，**调用方**要按数据来源取对应字段传入。
2. **`captchaKey` 必须按字符串处理**：雪花 ID 19 位，超出 JS `Number.MAX_SAFE_INTEGER`，后端已序列化为字符串，前端 `CaptchaVO.captchaKey: string` —— 不要改成 number。
3. **所有 ID 走字符串/后端已转字符串**：后端的雪花 ID 统一以字符串下发，避免精度丢失；前端 `GoalVO.id` 等为 `number` 仅因当前量级安全，**若后端改为雪花 ID 需同步改类型**。

### 5.3 分页约定

`PageResult<T>`：`{ page, size, total, pages, records }`。
分页参数统一 query 传递（`page` / `size` / 业务筛选），经 `qs()` 过滤空值，不会把空串传给后端。

---

## 6. 未接线清单（后端已提供，前端暂未调用）

以下 7 个已在 `api/index.ts` 定义好，但当前没有任何页面调用——**这是有意的预留，不是 bug**，接入时无需再写请求层：

| 前端方法 | 后端接口 | 建议接入场景 |
|---|---|---|
| `taskApi.upcoming(limit)` | `GET /api/tasks/upcoming` | 仪表盘「今日待办」卡片 |
| `recordApi.recent(limit)` | `GET /api/records/recent` | 仪表盘「最近记录」 |
| `materialApi.update(id, payload)` | `PUT /api/materials/{id}` | 资料重命名 / 改学科与描述 |
| `aiApi.tasks(params)` | `GET /api/ai/tasks` | AI 任务历史列表页 |
| `aiApi.search(q, topK)` | `GET /api/ai/search` | 独立语义检索入口 |
| `reminderApi.read(id)` | `POST /api/reminders/{id}/read` | 点击单条提醒标记已读 |
| `reminderApi.readAll()` | `POST /api/reminders/read-all` | 提醒中心「全部已读」 |

另有 `/internal/ai/progress`（AI 服务进度回调，走 `X-WitPath-AI-Token` 而非 JWT）属于**服务间内部接口**，前端不参与，详见 `docs/API.md`。

---

## 7. 错误处理与状态约定

| 层 | 行为 |
|---|---|
| 后端 | 统一 `Result<T> { code, msg, data }`；业务失败用非 200 code + 中文 msg |
| 前端 `unwrap` | `code !== 200` → `throw new Error(msg)`，视图 `catch` 后 `ui.toast(...)` |
| HTTP 401 | 清 store + `location.href = /login?redirect=<原路径>`，登录后跳转回去 |
| HTTP 403 | 无特殊处理，toast 展示后端 msg |
| 网络超时 | 按 1.3 的超时策略，toast 展示 axios message |

UI 状态约定：列表类接口统一配 骨架屏（加载）/ 空状态（无数据）/ 错误态（失败重试），并尊重 `prefers-reduced-motion`。

---

## 8. 联调自查清单

```bash
# 1. 后端探活（匿名接口，不需要 token）
curl http://127.0.0.1:8080/public/health

# 2. 前端 dev server（含代理）
curl -o /dev/null -w "%{http_code}\n" http://127.0.0.1:5173/

# 3. 登录拿 token（三段式，见 4.1）
#    登录成功写入 localStorage 键：witpath_token

# 4. 带 token 调业务接口
curl -H "Authorization: Bearer <token>" http://127.0.0.1:8080/api/user/profile
```

排查要点：
- **404 vs 401 混淆**：Spring Security 对**任何未匹配路径**在无 token 时也返回 401，不能据此判定路径存在。路径是否真实存在以 Controller 源码或 `docs/API.md` 总览表为准。
- **`/api` 重复前缀**：确认 `context-path` 为 `/`，真实路径形如 `/login/LoginInitId`、`/api/user/profile`，不要写成 `/api/login/...`。
- **改 store 后 HMR 不生效**：Pinia store 变动需重启 dev server。
- **后端 `java -jar` 运行时不要 `mvn package`**：会损坏运行中的 jar 导致全部接口 500；先停进程再打包。
