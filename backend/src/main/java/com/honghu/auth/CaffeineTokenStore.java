package com.honghu.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例部署下的默认令牌存储。TTL 随值一起存放，让每个令牌按自身有效期过期；
 * 另维护 subjectKey -> tokens 反向索引以支持批量注销。
 */
@Component
@ConditionalOnProperty(name = "token.store", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineTokenStore implements TokenStore {

    /** 令牌条目：所属身份 + 剩余有效期（纳秒），TTL 随值传给 Expiry。 */
    private record Entry(String subjectKey, long ttlNanos) {
    }

    private final Map<String, Set<String>> subjectToTokens = new ConcurrentHashMap<>();

    private final Cache<String, Entry> cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfter(new Expiry<String, Entry>() {
                @Override
                public long expireAfterCreate(String token, Entry entry, long currentTime) {
                    return entry.ttlNanos();
                }

                @Override
                public long expireAfterUpdate(String token, Entry entry, long currentTime, long currentDuration) {
                    return entry.ttlNanos();
                }

                @Override
                public long expireAfterRead(String token, Entry entry, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .removalListener((String token, Entry entry, RemovalCause cause) -> {
                if (token == null || entry == null) {
                    return;
                }
                subjectToTokens.computeIfPresent(entry.subjectKey(), (key, tokens) -> {
                    tokens.remove(token);
                    return tokens.isEmpty() ? null : tokens;
                });
            })
            .build();

    @Override
    public void save(String token, String subjectKey, long ttlSeconds) {
        long ttl = Duration.ofSeconds(Math.max(ttlSeconds, 1)).toNanos();
        subjectToTokens.computeIfAbsent(subjectKey, k -> ConcurrentHashMap.newKeySet()).add(token);
        cache.put(token, new Entry(subjectKey, ttl));
    }

    @Override
    public boolean isValid(String token) {
        return token != null && cache.getIfPresent(token) != null;
    }

    @Override
    public void revoke(String token) {
        if (token != null) {
            cache.invalidate(token);
        }
    }

    @Override
    public void revokeAll(String subjectKey) {
        Set<String> tokens = subjectToTokens.remove(subjectKey);
        if (tokens != null && !tokens.isEmpty()) {
            cache.invalidateAll(tokens);
        }
    }
}
