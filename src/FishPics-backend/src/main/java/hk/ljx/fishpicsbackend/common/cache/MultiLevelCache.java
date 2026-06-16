package hk.ljx.fishpicsbackend.common.cache;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存：Caffeine 做 L1（进程内），Redis 做 L2（分布式）
 * 一致性策略：读的时候 L1 miss 才查 L2；写和删都是先操作 L2 成功后再操作 L1
 *
 * 多节点部署时，evict 通过 Redisson RTopic 广播失效消息
 */
@Slf4j
public class MultiLevelCache<V> {

    /** 缓存失效广播 channel 前缀 */
    private static final String INVALIDATE_CHANNEL = "cache:invalidate:all";
    /** 是否已注册过全局监听 */
    private static volatile boolean LISTENER_REGISTERED = false;

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
    private final RedissonClient redisson;

    public MultiLevelCache(Cache<String, V> caffeineCache,
                           StringRedisTemplate redis,
                           String cacheName,
                           long l2TtlMinutes,
                           Class<V> valueType) {
        this(caffeineCache, redis, cacheName, l2TtlMinutes, valueType, null);
    }

    public MultiLevelCache(Cache<String, V> caffeineCache,
                           StringRedisTemplate redis,
                           String cacheName,
                           long l2TtlMinutes,
                           Class<V> valueType,
                           RedissonClient redisson) {
        this.caffeineCache = caffeineCache;
        this.redis = redis;
        this.cacheName = cacheName;
        this.l2TtlMinutes = l2TtlMinutes;
        this.valueType = valueType;
        this.redisson = redisson;
    }

    /**
     * 初始化全局缓存失效订阅
     */
    public static void initInvalidateListener(RedissonClient redissonClient) {
        if (redissonClient == null || LISTENER_REGISTERED) return;
        synchronized (MultiLevelCache.class) {
            if (LISTENER_REGISTERED) return;
            try {
                RTopic topic = redissonClient.getTopic(INVALIDATE_CHANNEL);
                // Redisson 的 addListener 接受类型化的 Listener
                topic.addListener(Serializable.class, (MessageListener<Serializable>) (channel, msg) -> {
                    try {
                        // msg 可能是 String、byte[] 等
                        String body = msg == null ? "" : msg.toString();
                        int colonIdx = body.indexOf(':');
                        if (colonIdx <= 0) return;
                        String cacheName = body.substring(0, colonIdx);
                        String key = body.substring(colonIdx + 1);
                        InvalidateListenerRegistry.getInstance().invalidateLocal(cacheName, key);
                    } catch (Exception e) {
                        log.warn("[CacheInvalidate] 解析失效广播失败: msg={}", msg, e);
                    }
                });
                LISTENER_REGISTERED = true;
                log.info("[CacheInvalidate] 已启动 L1 缓存失效广播订阅(channel={})", INVALIDATE_CHANNEL);
            } catch (Exception e) {
                log.warn("[CacheInvalidate] 启动失效广播订阅失败,L1 失效仅依赖 TTL(降级): {}", e.getMessage());
            }
        }
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

    // 写缓存，先写L2成功后再写L1（L2失败就不写L1）
    // put 后广播失效,让其他节点 L1 同步
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
        // 广播失效,让其他节点 L1 同步清掉
        try {
            if (redisson != null) {
                String body = cacheName + ":" + key;
                redisson.getTopic(INVALIDATE_CHANNEL).publish(body);
            }
        } catch (Exception e) {
            log.warn("多级缓存 put 后广播失效失败: cacheName={}, key={}", cacheName, key, e);
        }
    }

    // 清缓存，先删L2再删L1
    // 同时广播失效消息,让其他节点的 L1 也失效
    public void evict(String key) {
        try {
            redis.delete(buildRedisKey(key));
            // 广播失效(用 Redisson RTopic)
            if (redisson != null) {
                try {
                    String body = cacheName + ":" + key;
                    redisson.getTopic(INVALIDATE_CHANNEL).publish(body);
                } catch (Exception broadcastEx) {
                    log.debug("[CacheInvalidate] 广播失效失败(非阻塞): {}", broadcastEx.getMessage());
                }
            }
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

    /**
     * 暴露底层 Caffeine,用于 InvalidateListenerRegistry 注册到本地监听器
     */
    public Cache<String, V> getUnderlyingCaffeine() {
        return caffeineCache;
    }

    private String buildRedisKey(String key) {
        return cacheName + ":" + key;
    }

    /**
     * 失效广播注册表:cacheName -> 该 cacheName 在本节点的所有实例的 Caffeine
     */
    public static class InvalidateListenerRegistry {
        private static final InvalidateListenerRegistry INSTANCE = new InvalidateListenerRegistry();
        private final Map<String, List<Cache<String, ?>>> localCaches = new ConcurrentHashMap<>();

        public static InvalidateListenerRegistry getInstance() { return INSTANCE; }

        public void register(String cacheName, Cache<String, ?> cache) {
            localCaches.computeIfAbsent(cacheName, k -> new ArrayList<>()).add(cache);
        }

        public void invalidateLocal(String cacheName, String key) {
            List<Cache<String, ?>> caches = localCaches.get(cacheName);
            if (caches != null) {
                for (Cache<String, ?> c : caches) {
                    try { c.invalidate(key); } catch (Exception e) { log.debug("[CacheInvalidate] 本地失效失败, cacheName={}, key={}", cacheName, key, e); }
                }
            }
        }
    }
}
