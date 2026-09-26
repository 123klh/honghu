package com.honghu.auth;

/**
 * 令牌存储抽象：支持主动注销（服务端失效）与多端登录管理。
 * 默认 Caffeine 本地实现，可替换为 Redis 实现以支持多实例部署。
 */
public interface TokenStore {

    /** 登录时登记令牌，ttlSeconds 后自动过期。 */
    void save(String token, String subjectKey, long ttlSeconds);

    /** 令牌是否仍然有效（未注销、未过期）。 */
    boolean isValid(String token);

    /** 注销单个令牌。 */
    void revoke(String token);

    /** 注销某个身份的全部令牌（改密、封禁时使用）。 */
    void revokeAll(String subjectKey);
}
