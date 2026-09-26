package com.honghu.common;

/**
 * 当前登录身份的 ThreadLocal 上下文，由 AuthInterceptor 写入、请求结束时清理。
 */
public final class UserContext {

    public record Principal(Long id, String name, String type, String role) {
        public boolean isManager() {
            return "manager".equals(type);
        }
    }

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Principal principal) {
        HOLDER.set(principal);
    }

    public static Principal get() {
        return HOLDER.get();
    }

    /**
     * 取当前【用户】ID，未登录抛 401。
     *
     * <p>manager 与 user 各自独立编号（分表存储），同一个数字 ID 在两张表里指向
     * 不同的人。这里必须拒绝 manager 令牌，否则 manager #5 拿着自己的令牌调用面向
     * 用户的接口，会被当成 user #5 操作——错题本、考试记录都会张冠李戴。用户业务
     * 一律走本方法取 ID，从而把两套 ID 空间隔离开。
     */
    public static Long requireUserId() {
        Principal p = HOLDER.get();
        if (p == null) {
            throw new BusinessException(401, "未登录");
        }
        if (!"user".equals(p.type())) {
            throw new BusinessException(403, "该接口仅面向普通用户");
        }
        return p.id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
