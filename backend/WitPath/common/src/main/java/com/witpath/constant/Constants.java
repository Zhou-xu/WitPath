package com.witpath.constant;

/**
 * 全局常量定义
 */
public final class Constants {

    private Constants() {
    }

    /** 成功状态码 */
    public static final Integer CODE_SUCCESS = 200;

    /** 请求参数错误 */
    public static final Integer CODE_BAD_REQUEST = 400;

    /** 未认证 */
    public static final Integer CODE_UNAUTHORIZED = 401;

    /** 无权限 */
    public static final Integer CODE_FORBIDDEN = 403;

    /** 资源不存在 */
    public static final Integer CODE_NOT_FOUND = 404;

    /** 服务器内部错误 */
    public static final Integer CODE_ERROR = 500;

    /** 验证码 Redis Key 前缀 */
    public static final String REDIS_LOGIN_KEY = "witpath:login:init:";

    /** 同 IP 注册次数统计 Key 前缀（userlog 异常 IP 判定用） */
    public static final String REDIS_REGISTER_IP_COUNT_KEY = "witpath:register:ip:count:";

    /** 概览缓存 Key 前缀 */
    public static final String REDIS_DASHBOARD_KEY = "witpath:dashboard:";

    /** AI 任务幂等 Key 前缀 */
    public static final String REDIS_AI_TASK_KEY = "witpath:ai:task:";

    /** 用户个人大模型配置（解密后）缓存 Key 前缀，避免每次 AI 调用都读库解密 */
    public static final String REDIS_AI_CONFIG_KEY = "witpath:ai:config:";

    /** 当前登录用户请求属性名 */
    public static final String REQUEST_ATTR_USER_ID = "witpath.userId";

    /** AI 服务回调阶段性进度的接口路径 */
    public static final String PATH_AI_PROGRESS = "/internal/ai/progress";

    /** AI 服务回调使用的请求头 */
    public static final String HEADER_AI_CALLBACK_TOKEN = "X-WitPath-AI-Token";

    /** 单次 AI 分析允许携带的最大学习记录条数 */
    public static final int AI_CONTEXT_MAX_RECORDS = 120;

    /** 单次 AI 分析允许携带的最大目标数 */
    public static final int AI_CONTEXT_MAX_GOALS = 20;
}
