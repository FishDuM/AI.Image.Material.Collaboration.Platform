package hk.ljx.fishpicsbackend.common.cache;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 多级缓存：Caffeine 做 L1（进程内），Redis 做 L2（分布式）
 * 一致性策略：读的时候 L1 miss 才查 L2；写和删都是先操作 L2 成功后再操作 L1
 */
@Slf4j
public class MultiLevelCache<V> {

    /**
     * L2 包装器，用于保留泛型类型信息，避免 JSONUtil.toBean(json, Object.class) 丢失类型
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ValueWrapper {
        private Object data;
    }

    /**
     * 分段锁：固定 16 个锁对象，按 key hash 分配，避免 ConcurrentHashMap 无限增长
     */
    private static final int LOCK_SEGMENTS = 16;
    private static final Object[] LOCKS = new Object[LOCK_SEGMENTS];
    static {
        for (int i = 0; i < LOCK_SEGMENTS; i++) {
            LOCKS[i] = new Object();
        }
    }

    private final Cache<String, V> caffeineCache;
    private final StringRedisTemplate redis;
    private final String cacheName;
    private final long l2TtlMinutes;
    private final Class<V> valueType;

    public MultiLevelCache(Cache<String, V> caffeineCache,
                           StringRedisTemplate redis,
                           String cacheName,
                           long l2TtlMinutes,
                           Class<V> valueType) {
        this.caffeineCache = caffeineCache;
        this.redis = redis;
        this.cacheName = cacheName;
        this.l2TtlMinutes = l2TtlMinutes;
        this.valueType = valueType;
    }

    private Object getLock(String key) {
        return LOCKS[(key.hashCode() & 0x7fffffff) % LOCK_SEGMENTS];
    }

    // 读缓存，L1没命中再查L2
    @SuppressWarnings("unchecked")
    public V get(String key) {
        // 1. 查L1
        V value = caffeineCache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        // 2. 查L2（使用分段锁防止缓存击穿：同一 key 只允许一个线程回源）
        synchronized (getLock(key)) {
            // double-check：其他线程可能已经加载过了
            value = caffeineCache.getIfPresent(key);
            if (value != null) {
                return value;
            }
            try {
                String json = redis.opsForValue().get(buildRedisKey(key));
                if (StrUtil.isNotBlank(json)) {
                    ValueWrapper wrapper = JSONUtil.toBean(json, ValueWrapper.class);
                    V parsed = JSONUtil.parse(wrapper.getData()).toBean(valueType);
                    caffeineCache.put(key, parsed);
                    return parsed;
                }
            } catch (RuntimeException e) {
                log.warn("多级缓存L2读取失败, cacheName={}, key={}", cacheName, key, e);
            }
        }
        return null;
    }

    // 写缓存，先写L2成功后再写L1（L2失败就不写L1，避免本地有脏数据但Redis里没有）
    public void put(String key, V value) {
        try {
            // 用 ValueWrapper 包装，保留类型信息，避免 L2 反序列化时类型丢失
            ValueWrapper wrapper = new ValueWrapper(value);
            redis.opsForValue().set(buildRedisKey(key),
                    JSONUtil.toJsonStr(wrapper), l2TtlMinutes, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.warn("多级缓存L2写入失败, cacheName={}, key={}", cacheName, key, e);
            return; // L2写入失败则不写L1，避免脏读
        }
        caffeineCache.put(key, value);
    }

    // 清缓存，先删L2再删L1；L2删失败时不删L1，让本地缓存继续服务，避免穿透到L2读到旧数据
    public void evict(String key) {
        try {
            redis.delete(buildRedisKey(key));
        } catch (RuntimeException e) {
            log.warn("多级缓存L2删除失败, cacheName={}, key={}", cacheName, key, e);
            return; // L2删失败时不删L1，避免下次读到L2的旧数据
        }
        caffeineCache.invalidate(key);
    }

    // 清空所有L1（用于帖子列表SCAN清除后调用）
    public void evictAllL1() {
        caffeineCache.invalidateAll();
    }

    private String buildRedisKey(String key) {
        return cacheName + ":" + key;
    }
}
