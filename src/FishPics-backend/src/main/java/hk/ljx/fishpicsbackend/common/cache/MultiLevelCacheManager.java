package hk.ljx.fishpicsbackend.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import hk.ljx.fishpicsbackend.common.constants.CacheConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// 管理各个多级缓存实例
@Component
public class MultiLevelCacheManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 注入 Redisson 客户端
     * 用于 L1 缓存失效广播
     */
    @Resource
    private RedissonClient redissonClient;

    // 用户信息缓存
    private MultiLevelCache<Object> userInfoCache;
    // 用户权限缓存
    private MultiLevelCache<Object> userPermCache;
    // 系统配置缓存
    private MultiLevelCache<Object> sysConfigCache;

    public MultiLevelCache<Object> getUserInfoCache() { return userInfoCache; }
    public MultiLevelCache<Object> getUserPermCache() { return userPermCache; }
    public MultiLevelCache<Object> getSysConfigCache() { return sysConfigCache; }

    @PostConstruct
    public void init() {
        MultiLevelCache.initInvalidateListener(redissonClient);

        userInfoCache = new MultiLevelCache<>(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(CacheConstants.L1_USER_INFO, TimeUnit.SECONDS)
                        .build(),
                stringRedisTemplate,
                CacheConstants.USER_INFO,
                CacheConstants.L2_USER_INFO,
                Object.class,
                redissonClient
        );
        MultiLevelCache.InvalidateListenerRegistry.getInstance()
                .register(CacheConstants.USER_INFO, userInfoCache.getUnderlyingCaffeine());

        userPermCache = new MultiLevelCache<>(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(CacheConstants.L1_USER_PERMISSIONS, TimeUnit.SECONDS)
                        .build(),
                stringRedisTemplate,
                CacheConstants.USER_PERMISSIONS,
                CacheConstants.L2_USER_PERMISSIONS,
                Object.class,
                redissonClient
        );
        MultiLevelCache.InvalidateListenerRegistry.getInstance()
                .register(CacheConstants.USER_PERMISSIONS, userPermCache.getUnderlyingCaffeine());

        sysConfigCache = new MultiLevelCache<>(
                Caffeine.newBuilder()
                        .maximumSize(20)
                        .expireAfterWrite(CacheConstants.L1_SYSTEM_CONFIG, TimeUnit.SECONDS)
                        .build(),
                stringRedisTemplate,
                CacheConstants.SYSTEM_CONFIG,
                CacheConstants.L2_SYSTEM_CONFIG,
                Object.class,
                redissonClient
        );
        MultiLevelCache.InvalidateListenerRegistry.getInstance()
                .register(CacheConstants.SYSTEM_CONFIG, sysConfigCache.getUnderlyingCaffeine());
    }
}
