package hk.ljx.fishpicsbackend.common.cache;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

// 多级缓存，Caffeine做L1，Redis做L2
@Slf4j
public class MultiLevelCache<V> {

    private final Cache<String, V> caffeineCache;
    private final StringRedisTemplate redis;
    private final String cacheName;
    private final long l2TtlMinutes;

    public MultiLevelCache(Cache<String, V> caffeineCache,
                           StringRedisTemplate redis,
                           String cacheName,
                           long l2TtlMinutes) {
        this.caffeineCache = caffeineCache;
        this.redis = redis;
        this.cacheName = cacheName;
        this.l2TtlMinutes = l2TtlMinutes;
    }

    // 读缓存，L1没命中再查L2
    @SuppressWarnings("unchecked")
    public V get(String key) {
        // 1. 查L1
        V value = caffeineCache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        // 2. 查L2
        try {
            String json = redis.opsForValue().get(buildRedisKey(key));
            if (StrUtil.isNotBlank(json)) {
                // parse比toBean兼容性更好，对象和数组都能处理
                V parsed = (V) JSONUtil.parse(json);
                caffeineCache.put(key, parsed);
                return parsed;
            }
        } catch (RuntimeException e) {
            log.warn("多级缓存L2读取失败, cacheName={}, key={}", cacheName, key, e);
        }
        return null;
    }

    // 写缓存，同时写L1和L2
    public void put(String key, V value) {
        caffeineCache.put(key, value);
        try {
            redis.opsForValue().set(buildRedisKey(key),
                    JSONUtil.toJsonStr(value), l2TtlMinutes, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.warn("多级缓存L2写入失败, cacheName={}, key={}", cacheName, key, e);
        }
    }

    // 清缓存，L1和L2都删
    public void evict(String key) {
        caffeineCache.invalidate(key);
        try {
            redis.delete(buildRedisKey(key));
        } catch (RuntimeException e) {
            log.warn("多级缓存L2删除失败, cacheName={}, key={}", cacheName, key, e);
        }
    }

    // 清空所有L1（用于帖子列表SCAN清除后调用）
    public void evictAllL1() {
        caffeineCache.invalidateAll();
    }

    private String buildRedisKey(String key) {
        return cacheName + ":" + key;
    }
}
