package hk.ljx.fishpicsbackend.common.cache;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisTtlCache {

    private final StringRedisTemplate redis;
    private final String cacheName;
    private final long ttlMinutes;

    public RedisTtlCache(StringRedisTemplate redis, String cacheName, long ttlMinutes) {
        this.redis = redis;
        this.cacheName = cacheName;
        this.ttlMinutes = ttlMinutes;
    }

    public <T> T get(String key, Class<T> valueType) {
        String json = read(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.toBean(json, valueType);
        } catch (RuntimeException e) {
            log.warn("Redis cache read failed, cacheName={}, key={}", cacheName, key, e);
            evict(key);
            return null;
        }
    }

    public <T> List<T> getList(String key, Class<T> itemType) {
        String json = read(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.toList(json, itemType);
        } catch (RuntimeException e) {
            log.warn("Redis cache list read failed, cacheName={}, key={}", cacheName, key, e);
            evict(key);
            return null;
        }
    }

    public void put(String key, Object value) {
        put(key, value, ttlMinutes, TimeUnit.MINUTES);
    }

    public void put(String key, Object value, long timeout, TimeUnit unit) {
        if (value == null) {
            evict(key);
            return;
        }
        try {
            redis.opsForValue().set(buildRedisKey(key), JSONUtil.toJsonStr(value), timeout, unit);
        } catch (RuntimeException e) {
            log.warn("Redis cache write failed, cacheName={}, key={}", cacheName, key, e);
        }
    }

    public void evict(String key) {
        try {
            redis.delete(buildRedisKey(key));
        } catch (RuntimeException e) {
            log.warn("Redis cache evict failed, cacheName={}, key={}", cacheName, key, e);
        }
    }

    private String read(String key) {
        try {
            return redis.opsForValue().get(buildRedisKey(key));
        } catch (RuntimeException e) {
            log.warn("Redis cache get failed, cacheName={}, key={}", cacheName, key, e);
            return null;
        }
    }

    private String buildRedisKey(String key) {
        return cacheName + ":" + key;
    }
}
