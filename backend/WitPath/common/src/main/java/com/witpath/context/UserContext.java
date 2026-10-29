package com.witpath.context;

/**
 * 当前登录用户上下文（基于 ThreadLocal，请求结束后由过滤器清理）
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取当前用户 ID，未登录时抛出业务异常
     */
    public static Long requireUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new com.witpath.exception.BusinessException(401, "登录状态已失效，请重新登录");
        }
        return userId;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
