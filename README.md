# WitPath · 学途知行 AI 协作平台

面向个人学习的「记录 — 分析 — 建议」闭环平台：把学习目标、任务与学习记录沉淀为可追溯的数据，
再由 Python AI 服务（LangGraph 多 Agent）生成分析洞察、学习计划与资料问答。

- 前端：Vue 3 + TypeScript + Vite + Pinia + Axios + ECharts
- 后端：Spring Boot 3.5（Spring Security + JWT + MyBatis-Plus）+ MySQL + Redis + RabbitMQ
- AI 服务：FastAPI + LangChain + LangGraph + PostgreSQL(checkpoint) + Milvus(向量库) + 阿里云 OSS

AI 相关逻辑全部位于 `Agent/`（FastAPI）内，Spring Boot 只负责数据组装、权限与任务编排。

---

## 1. 目录结构

```
WitPath/
├── database/WitPath.sql        # MySQL 建表脚本（幂等，含演示账号）
├── backend/WitPath/            # Spring Boot 多模块工程
│   ├── common/                 # 常量、枚举、工具类、上下文、配置属性
│   ├── pojo/                   # entity / dto / vo
│   └── server/                 # controller / service / mapper / mq / ai / config
├── Agent/                      # FastAPI AI 服务（LangGraph 多 Agent）
│   ├── app/agents/             # 状态定义、节点、图编排、提示词
│   ├── app/routers/            # health / ai / documents
│   └── app/services/           # 回调、记忆(PostgreSQL)、检索(Milvus)、摄取、嵌入
├── frontend/WitPath/           # Vue 3 前端
├── docker/docker-compose.yml    # 中间件一键编排（MySQL/Redis/RabbitMQ/PostgreSQL/Milvus/Ollama）
├── tools/                      # 数据库脚本与端到端冒烟测试
└── docs/说明.txt                # 原始需求与架构说明
```

## 2. 核心能力

| 模块 | 说明 |
| --- | --- |
| 学习记录 | 记录时长 / 专注度 / 学科 / 成果，自动回算目标与任务统计 |
| 目标计划 | 目标拆解为任务，进度由任务完成度或投入时长自动计算 |
| 进度分析 | 概览看板：趋势、学科分布、连续打卡、目标进度、AI 洞察 |
| AI 助手 | 多 Agent 分析任务（进度实时回推）+ 基于个人资料库的语义问答 |
| 资料管理 | 上传 PDF/Word/文本 → 解析切分 → 向量化入库（Milvus） |
| 提醒建议 | 依据目标与任务自动生成提醒，支持已读与批量处理 |

## 3. 本地启动

### 3.1 依赖中间件

| 组件 | 默认地址 | 说明 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3306`（SSH 隧道 → 云服务器 42.192.51.230） | 库名 `WitPath`，执行 `database/WitPath.sql` |
| Redis | `127.0.0.1:6379`（SSH 隧道） | 登录会话、验证码、看板缓存 |
| RabbitMQ | `127.0.0.1:5672`（管理端 15672，SSH 隧道） | AI 任务与文档摄取队列，不可用时自动降级本地线程池 |
| PostgreSQL | `127.0.0.1:5432`（SSH 隧道） | LangGraph checkpoint，不可用时降级内存 |
| Milvus | `127.0.0.1:19530`（SSH 隧道） | 向量库，不可用时降级内存检索 |
| Embedding | 阿里云百炼 `qwen3.7-text-embedding`（1024 维） | 线上向量模型，需配置 `DASHSCOPE_API_KEY`；不可用时自动降级哈希向量 |
| Ollama（可选） | `127.0.0.1:11434` | 备用本地 Embedding（`bge-m3`），设置 `EMBEDDING_PROVIDER=ollama` 启用 |

均可通过环境变量覆盖，见 `application-dev.yaml` 与 `Agent/.env.example`。

若本机没有现成的中间件，可用编排文件一次性拉起（端口与账号已与默认配置对齐）：

```bash
cd docker && docker compose up -d              # 全量
docker compose up -d mysql redis rabbitmq      # 只起业务必需项
docker exec -it witpath-ollama ollama pull bge-m3   # 只需一次，向量模型
```

### 3.2 数据库

```bash
python tools/db_cli.py exec database/WitPath.sql
```

### 3.3 后端

```bash
cd backend/WitPath
mvn -pl common,pojo install -DskipTests     # 依赖模块先安装
mvn -pl server spring-boot:run              # 默认 8080
```

敏感配置（OSS 密钥等）放在**不入库**的
`backend/WitPath/server/src/main/resources/application-secret.yaml` 或
`backend/WitPath/server/config/application-secret.yaml`，模板见 `application-dev.yaml` 末尾说明。

### 3.4 AI 服务

```bash
cd Agent
python -m venv .venv && .venv/Scripts/pip install -r requirements.txt
cp .env.example .env        # 填入 ARK_API_KEY / PostgreSQL / Milvus / OSS 等
.venv/Scripts/python run.py # 默认 8000
```

### 3.5 前端

```bash
cd frontend/WitPath
npm install
npm run dev      # http://localhost:5173，已配置 /api 代理到 8080
npm run build    # 类型检查 + 打包
```

## 4. AI 任务链路

1. 前端 `POST /api/ai/analyze` → Spring Boot 落库 `ai_task`（状态排队中）
2. `TaskDispatcher` 投递 RabbitMQ（`q.witpath.ai.task`），MQ 不可用时走本地线程池
3. 消费者调用 FastAPI `/api/ai/analyze`，携带学习上下文与回调地址
4. LangGraph 执行 `数据整理 → 态势分析 → 计划生成 → 资料检索 → 建议生成 → 结论整合`，
   每个节点通过 `/internal/ai/progress`（带 `X-WitPath-AI-Token`）回推阶段与进度
5. 前端轮询 `/api/ai/tasks/{taskNo}` 展示实时进度，完成后渲染 Markdown 结论

模型不可用时自动降级为规则引擎，保证链路可用。

## 5. 端到端验证

```bash
python tools/smoke_test.py
```

覆盖：三步登录 → 资料 → 目标/任务 → 学习记录 → 概览 → 资料上传与向量化 →
AI 分析任务 → AI 对话 → 提醒生成 → 测试数据清理。脚本从 Redis 读取验证码答案用于自动化。

## 6. 测试

| 范围 | 命令 | 说明 |
| --- | --- | --- |
| 后端 | `cd backend/WitPath && mvn test` | 32 个用例：JWT / 雪花 ID / 日期工具 / 统一响应 / AI 阶段枚举 / AI 载荷序列化 / 目标进度重算（含 Spring 上下文加载） |
| AI 服务 | `cd Agent && .venv\Scripts\python.exe -m unittest discover -s tests -t . -v` | 36 个离线用例：camelCase 契约、哈希向量降级、内存库隔离、进度回调契约、HTTP 接口契约 |
| 前端 | `cd frontend/WitPath && npm run build` | `vue-tsc` 类型检查 + 生产打包 |
| 端到端 | `python tools/smoke_test.py` | 17 步真实 HTTP 全链路（注册/登录/目标/任务/记录/上传/入库/AI 分析/对话/清理） |

后端单元测试不依赖任何外部中间件（`GoalServiceTest` 用 Mockito 打桩 Mapper，
并通过 `TableInfoHelper.initTableInfo` 手动初始化 MyBatis-Plus 实体元数据）；
AI 侧用例固定走规则引擎 + 内存向量库，不会消耗模型额度或访问外网。

## 7. 已知事项

- 阿里云 OSS 使用**内网 Endpoint** 时只能在同地域 ECS 访问，本地开发建议 `witpath.storage.type=local`；
  切到 `oss` 需保证外网 Endpoint 与密钥正确。
- Milvus / PostgreSQL 不可用时 AI 服务自动降级（健康接口 `vector.backend` 会标注
  `memory(milvus-unavailable)`），修复中间件后刷新即可恢复。
