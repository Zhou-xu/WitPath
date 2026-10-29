-- =============================================================================
-- 学途知行 AI 协作平台（WitPath） MySQL 建表 / 初始化脚本
-- =============================================================================
-- 目标版本：MySQL 8.0+
-- 字符集  ：utf8mb4 / utf8mb4_0900_ai_ci
-- 设计约定：
--   1. 业务表统一使用 BIGINT 自增主键，逻辑删除字段 is_deleted（0 未删除 / 1 已删除）。
--   2. 业务表统一携带 created_at / updated_at，updated_at 由数据库自动刷新，
--      同时由 MyBatis-Plus MetaObjectHandler 兜底填充。
--   3. 状态/类型字段使用 TINYINT，取值含义见字段注释与 com.witpath.common.enums。
--   4. study_goal 上的 actual_hours / task_done 等冗余统计字段由业务层维护，
--      用于列表排序与展示；明细事实以 study_record 为准。
--   5. 脚本幂等：建表使用 CREATE TABLE IF NOT EXISTS，初始化数据按用户名去重，可重复执行。
--
-- 执行方式：
--   mysql -h <host> -u root -p < database/WitPath.sql
--   或：python tools/db_cli.py exec database/WitPath.sql
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `WitPath`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `WitPath`;

-- -----------------------------------------------------------------------------
-- 用户表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username        VARCHAR(50)  NOT NULL COMMENT '用户名/登录名',
    password_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希，禁止明文',
    nickname        VARCHAR(50)  DEFAULT ''   COMMENT '昵称',
    avatar_url      VARCHAR(255) DEFAULT ''   COMMENT '头像地址',
    email           VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone           VARCHAR(20)  DEFAULT NULL COMMENT '手机号',

    role            TINYINT      NOT NULL DEFAULT 1 COMMENT '角色：1普通用户 2管理员',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 2禁用 3待激活',

    study_stage     VARCHAR(50)  DEFAULT NULL COMMENT '学习阶段：高中/大学/考研/职场等',
    study_goal      VARCHAR(255) DEFAULT NULL COMMENT '当前学习目标',
    preferred_tags  VARCHAR(255) DEFAULT NULL COMMENT '学习偏好标签，如英语、编程、考研数学',

    last_login_at   DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone),
    KEY idx_status (status),
    KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------------------------
-- 学习目标表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_goal (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '目标ID',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    title           VARCHAR(120) NOT NULL COMMENT '目标标题',
    description     VARCHAR(500) DEFAULT NULL COMMENT '目标描述',
    subject         VARCHAR(50)  DEFAULT NULL COMMENT '学科/分类，如数学、英语、编程',

    priority        TINYINT      NOT NULL DEFAULT 2 COMMENT '优先级：1低 2中 3高',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1进行中 2已完成 3已暂停 4已归档',

    start_date      DATE         DEFAULT NULL COMMENT '开始日期',
    end_date        DATE         DEFAULT NULL COMMENT '截止日期',

    target_hours    DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '计划投入小时数',
    actual_hours    DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '实际投入小时数（由学习记录汇总）',
    task_total      INT          NOT NULL DEFAULT 0 COMMENT '关联任务总数',
    task_done       INT          NOT NULL DEFAULT 0 COMMENT '已完成任务数',
    progress        DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '完成进度百分比 0~100',

    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    KEY idx_user_status (user_id, status, is_deleted),
    KEY idx_end_date (end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习目标表';

-- -----------------------------------------------------------------------------
-- 学习任务表（目标的阶段性拆解，也允许独立于目标存在）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_task (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    goal_id           BIGINT UNSIGNED DEFAULT NULL COMMENT '所属目标ID，可为空',
    title             VARCHAR(200) NOT NULL COMMENT '任务标题',
    description       VARCHAR(500) DEFAULT NULL COMMENT '任务描述',

    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1待办 2进行中 3已完成 4已延期',
    priority          TINYINT      NOT NULL DEFAULT 2 COMMENT '优先级：1低 2中 3高',

    plan_date         DATE         DEFAULT NULL COMMENT '计划执行日期',
    due_date          DATE         DEFAULT NULL COMMENT '截止日期',

    estimated_minutes INT          NOT NULL DEFAULT 0 COMMENT '预计时长（分钟）',
    actual_minutes    INT          NOT NULL DEFAULT 0 COMMENT '实际已完成时长（分钟）',
    completed_at      DATETIME     DEFAULT NULL COMMENT '完成时间',
    sort_order        INT          NOT NULL DEFAULT 0 COMMENT '排序序号，越大越靠前',

    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    KEY idx_user_status (user_id, status, is_deleted),
    KEY idx_goal (goal_id),
    KEY idx_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习任务表';

-- -----------------------------------------------------------------------------
-- 学习记录表（最小学习事实单元，所有统计的基础）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_record (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    goal_id          BIGINT UNSIGNED DEFAULT NULL COMMENT '关联目标ID',
    task_id          BIGINT UNSIGNED DEFAULT NULL COMMENT '关联任务ID',

    title            VARCHAR(200) NOT NULL COMMENT '学习内容标题',
    content          TEXT         DEFAULT NULL COMMENT '学习内容/笔记详情',
    subject          VARCHAR(50)  DEFAULT NULL COMMENT '学科/分类',

    study_date       DATE         NOT NULL COMMENT '学习日期',
    start_at         DATETIME     DEFAULT NULL COMMENT '开始时间',
    end_at           DATETIME     DEFAULT NULL COMMENT '结束时间',
    duration_minutes INT          NOT NULL DEFAULT 0 COMMENT '学习时长（分钟）',

    focus_score      TINYINT      DEFAULT NULL COMMENT '专注度评分 1~5',
    outcome          VARCHAR(500) DEFAULT NULL COMMENT '学习成果/收获',
    tags             VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔',

    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    KEY idx_user_date (user_id, study_date, is_deleted),
    KEY idx_user_subject (user_id, subject),
    KEY idx_goal (goal_id),
    KEY idx_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

-- -----------------------------------------------------------------------------
-- 学习资料表（保存元数据；实体文件存放于 OSS 或本地存储目录）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS study_material (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '资料ID',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    name           VARCHAR(255) NOT NULL COMMENT '资料名称',
    original_name  VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    file_type      VARCHAR(32)  DEFAULT NULL COMMENT '文件扩展名，如 pdf / docx / md',

    storage_type   TINYINT      NOT NULL DEFAULT 1 COMMENT '存储类型：1本地磁盘 2阿里云OSS',
    local_path     VARCHAR(500) DEFAULT NULL COMMENT '本地相对存储路径',
    oss_bucket     VARCHAR(128) DEFAULT NULL COMMENT 'OSS Bucket 名称',
    oss_object_key VARCHAR(500) DEFAULT NULL COMMENT 'OSS 对象标识 Key',
    oss_url        VARCHAR(1000) DEFAULT NULL COMMENT 'OSS 访问地址',

    file_size      BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    subject        VARCHAR(50)  DEFAULT NULL COMMENT '学科/分类',
    description    VARCHAR(500) DEFAULT NULL COMMENT '资料描述',

    parse_status   TINYINT      NOT NULL DEFAULT 0 COMMENT '解析状态：0未解析 1解析中 2已完成 3失败',
    vector_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '向量化状态：0未处理 1处理中 2已完成 3失败',
    chunk_count    INT          NOT NULL DEFAULT 0 COMMENT '切分后的文本片段数量',
    error_message  VARCHAR(500) DEFAULT NULL COMMENT '处理失败原因',

    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    KEY idx_user (user_id, is_deleted),
    KEY idx_vector_status (vector_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习资料表';


-- -----------------------------------------------------------------------------
-- AI 任务表（Spring Boot 发起、FastAPI + LangGraph 执行的异步任务留痕）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_task (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    task_no        VARCHAR(36)  NOT NULL COMMENT '任务编号（对外暴露，UUID）',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '发起用户ID',
    type           VARCHAR(32)  NOT NULL COMMENT '任务类型：STUDY_ANALYSIS / STUDY_PLAN / SUMMARY / CHAT',

    title          VARCHAR(200) DEFAULT NULL COMMENT '任务标题',
    question       VARCHAR(1000) DEFAULT NULL COMMENT '用户提问/分析问题',
    focus_goal_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '聚焦分析的目标ID，为空表示分析全部进行中目标',

    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0排队中 1执行中 2已完成 3失败 4已取消',
    stage          VARCHAR(50)  DEFAULT NULL COMMENT '当前执行阶段（对应 LangGraph 节点名）',
    stage_message  VARCHAR(255) DEFAULT NULL COMMENT '阶段描述',
    progress       INT          NOT NULL DEFAULT 0 COMMENT '总体进度百分比 0~100',

    request_json   LONGTEXT     DEFAULT NULL COMMENT '提交给 AI 服务的请求快照',
    result_json    LONGTEXT     DEFAULT NULL COMMENT 'AI 服务返回的完整结构化结果',
    summary        VARCHAR(1000) DEFAULT NULL COMMENT '结论摘要',
    suggestion     LONGTEXT     DEFAULT NULL COMMENT '改进建议（Markdown）',

    model          VARCHAR(64)  DEFAULT NULL COMMENT '使用的模型标识',
    tokens_used    INT          NOT NULL DEFAULT 0 COMMENT '消耗的 token 数量',
    latency_ms     INT          NOT NULL DEFAULT 0 COMMENT '端到端耗时（毫秒）',
    error_message  VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',

    started_at     DATETIME     DEFAULT NULL COMMENT '开始执行时间',
    finished_at    DATETIME     DEFAULT NULL COMMENT '完成时间',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    UNIQUE KEY uk_task_no (task_no),
    KEY idx_user_created (user_id, created_at),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 任务表';

-- -----------------------------------------------------------------------------
-- AI 会话 / 消息表（AI 助手多轮对话）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_conversation (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    title           VARCHAR(120) DEFAULT '新的对话' COMMENT '会话标题',
    message_count   INT          NOT NULL DEFAULT 0 COMMENT '消息条数',
    last_message_at DATETIME     DEFAULT NULL COMMENT '最后一条消息时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    KEY idx_user (user_id, is_deleted, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话表';

CREATE TABLE IF NOT EXISTS ai_message (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    conversation_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话ID',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    role            VARCHAR(16)  NOT NULL COMMENT '角色：user / assistant',
    content         LONGTEXT     NOT NULL COMMENT '消息内容',
    referenced      VARCHAR(500) DEFAULT NULL COMMENT '引用的资料片段摘要',
    tokens_used     INT          NOT NULL DEFAULT 0 COMMENT '本条消息消耗 token',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    KEY idx_conversation (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话消息表';

-- -----------------------------------------------------------------------------
-- 提醒表（任务到期、目标临期、AI 建议等）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reminder (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '提醒ID',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
    goal_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '关联目标ID',
    task_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '关联任务ID',

    type         VARCHAR(32)  NOT NULL COMMENT '类型：TASK_DUE / GOAL_DEADLINE / AI_SUGGESTION / DAILY',
    level        TINYINT      NOT NULL DEFAULT 1 COMMENT '级别：1普通 2重要 3紧急',
    title        VARCHAR(200) NOT NULL COMMENT '提醒标题',
    content      VARCHAR(500) DEFAULT NULL COMMENT '提醒内容',

    source       VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM' COMMENT '来源：SYSTEM / AI',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0未读 1已读 2已处理',
    trigger_time DATETIME     DEFAULT NULL COMMENT '触发时间',
    read_at      DATETIME     DEFAULT NULL COMMENT '读取时间',

    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    KEY idx_user_status (user_id, status, is_deleted),
    KEY idx_trigger (trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提醒表';

-- -----------------------------------------------------------------------------
-- 用户个人大模型配置表
-- 说明：
--   1. API Key 一律以 AES-GCM 密文落库（Base64：12字节IV + 密文 + 16字节Tag），
--      明文只存在于内存与 Redis（TTL 5 分钟），解密密钥取自环境变量 APP_SECRET_KEY。
--   2. 一人一服务商一条（uk_user_provider）；删除配置走物理删除，避免逻辑删除
--      残留行与唯一键冲突导致用户无法重新配置。
--   3. embedding_* 为「高级配置」，留空表示该能力回落服务端默认配置。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_api_key (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id               BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    provider              VARCHAR(32)  NOT NULL DEFAULT 'openai-compatible' COMMENT '服务商标识：openai-compatible / ark / dashscope 等',

    api_key_enc           TEXT         NOT NULL COMMENT '大模型 API Key（AES-GCM 密文）',
    base_url              VARCHAR(255) NOT NULL COMMENT '大模型请求地址（OpenAI 兼容端点）',
    model                 VARCHAR(128) NOT NULL COMMENT '选定的大模型标识',

    embedding_provider    VARCHAR(32)  DEFAULT NULL COMMENT '向量模型服务商，留空表示不覆盖系统默认',
    embedding_model       VARCHAR(128) DEFAULT NULL COMMENT '向量模型标识',
    embedding_api_key_enc TEXT         DEFAULT NULL COMMENT '向量模型 API Key（AES-GCM 密文）',
    embedding_base_url    VARCHAR(255) DEFAULT NULL COMMENT '向量模型请求地址',

    status                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    use_default_api       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否使用系统默认模型：1是 0否（切回默认时保留原配置，可随时改回）',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',

    UNIQUE KEY uk_user_provider (user_id, provider),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户个人大模型配置表';

-- =============================================================================
-- 初始化数据（幂等：按用户名去重）
-- =============================================================================
-- 演示账号（首次登录后建议立即修改密码,下面为示例）：
--   admin / admin123   管理员
--   user  / user123     普通用户
-- 密码均为 BCrypt(cost=10) 哈希，库内不保存明文。
INSERT INTO user (username, password_hash, nickname, avatar_url, email, role, status,
                  study_stage, study_goal, preferred_tags)
SELECT 'admin', '$2b$10$1BWvT54Dhc9A6P1pWrZPveHtAPLIEB2wm/vdivoE5xS3a9hFXHrde', '平台管理员', '', 'admin@witpath.dev', 2, 1,
       '职场', '构建 AI 时代的个人学习体系', 'AI,系统设计,英语'
WHERE NOT EXISTS (SELECT 1 FROM user WHERE username = 'admin');

INSERT INTO user (username, password_hash, nickname, avatar_url, email, role, status,
                  study_stage, study_goal, preferred_tags)
SELECT 'demo', '$2b$10$/DAuyHymc6rIY7yKFyjJ0Oi.CIHElcc81BEWrsse.H8TiyEmwJMVK', '知行同学', '', 'demo@witpath.dev', 1, 1,
       '大学', '三个月内拿下考研数学基础阶段', '考研数学,英语,编程'
WHERE NOT EXISTS (SELECT 1 FROM user WHERE username = 'demo');
