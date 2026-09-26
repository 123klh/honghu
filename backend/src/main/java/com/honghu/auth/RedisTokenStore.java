package com.honghu.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 多实例部署时的令牌存储：token -> subjectKey 单键 + subjectKey 下的 token 集合。
 * 启用方式：application.properties 设置 token.store=redis 并配置 spring.data.redis.*。
 */
@Component
@ConditionalOnProperty(name = "token.store", havingValue = "redis")
public class RedisTokenStore implements TokenStore {

    private static final String TOKEN_PREFIX = "honghu:token:";
    private static final String SUBJECT_PREFIX = "honghu:subject:";

    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String token, String subjectKey, long ttlSeconds) {
        Duration ttl = Duration.ofSeconds(Math.max(ttlSeconds, 1));
        redis.opsForValue().set(TOKEN_PREFIX + token, subjectKey, ttl);
        String subjectSet = SUBJECT_PREFIX + subjectKey;
        redis.opsForSet().add(subjectSet, token);
        // 索引集合的过期时间不短于其中最新的令牌，避免悬挂键无限增长
        redis.expire(subjectSet, ttl.plusDays(1));
    }

    @Override
    public boolean isValid(String token) {
        return token != null && Boolean.TRUE.equals(redis.hasKey(TOKEN_PREFIX + token));
    }

    @Override
    public void revoke(String token) {
        if (token == null) {
            return;
        }
        String subjectKey = redis.opsForValue().get(TOKEN_PREFIX + token);
        redis.delete(TOKEN_PREFIX + token);
        if (subjectKey != null) {
            redis.opsForSet().remove(SUBJECT_PREFIX + subjectKey, token);
        }
    }

    @Override
    public void revokeAll(String subjectKey) {
        String subjectSet = SUBJECT_PREFIX + subjectKey;
        Set<String> tokens = redis.opsForSet().members(subjectSet);
        if (tokens != null && !tokens.isEmpty()) {
            redis.delete(tokens.stream().map(t -> TOKEN_PREFIX + t).toList());
        }
        redis.delete(subjectSet);
    }
}
