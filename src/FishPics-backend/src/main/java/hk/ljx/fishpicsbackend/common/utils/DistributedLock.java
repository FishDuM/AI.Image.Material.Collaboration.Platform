package hk.ljx.fishpicsbackend.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁(基于 SETNX + Lua 释放)
 *
 * 替代 synchronized，多节点可用
 */
@Slf4j
public class DistributedLock {

    /** SET key value EX seconds NX */
    private static final String LUA_RELEASE =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('DEL', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private static final RedisScript<Long> SCRIPT_RELEASE =
            new DefaultRedisScript<>(LUA_RELEASE, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final String key;
    private final long ttlSeconds;
    private final String token;
    private boolean acquired = false;

    public DistributedLock(StringRedisTemplate stringRedisTemplate, String key, long ttlSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.key = key;
        this.ttlSeconds = ttlSeconds;
        this.token = UUID.randomUUID().toString();
    }

    /** 尝试获取锁,返回 true 表示拿到 */
    public boolean tryLock() {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttlSeconds, TimeUnit.SECONDS);
        acquired = Boolean.TRUE.equals(ok);
        if (!acquired) {
            log.debug("[DistributedLock] 获取锁失败: key={}", key);
        }
        return acquired;
    }

    /** 释放锁(Lua 原子校验 token,防误删) */
    public void unlock() {
        if (!acquired) return;
        try {
            stringRedisTemplate.execute(SCRIPT_RELEASE, Collections.singletonList(key), token);
        } catch (Exception e) {
            log.warn("[DistributedLock] 释放锁失败: key={}", key, e);
        } finally {
            acquired = false;
        }
    }

    public String getKey() { return key; }
}
