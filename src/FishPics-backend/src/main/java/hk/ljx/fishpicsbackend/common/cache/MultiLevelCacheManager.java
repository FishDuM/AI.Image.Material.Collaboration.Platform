package hk.ljx.fishpicsbackend.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import hk.ljx.fishpicsbackend.common.constants.CacheConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// 管理各个多级缓存实例
@Component
public class MultiLevelCacheManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 用户信息缓存
    public MultiLevelCache<Object> userInfoCache;
    // 用户权限缓存
    public MultiLevelCache<Object> userPermCache;
    // 帖子列表缓存
    public MultiLevelCache<Object> postListCache;
    // 系统配置缓存
    public MultiLevelCache<Object> sysConfigCache;

    @PostConstruct
    public void init() {
        userInfoCache = new MultiLevelCache<>(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(CacheConstants.L1_USER_INFO, TimeUnit.SECONDS)
                        .build(),
                stringRedisTemplate,
                CacheConstants.USER_INFO,
                CacheConstants.L2_USER_INFO
        );

        userPermCache = new MultiLevelCache<>(
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(CacheConstants.L1_USER_PERMISSIONS, TimeUnit.SECONDS)
                        .build(),
                stringRedisTemplate,
                CacheConstants.USER_PERMISSIONS,
                CacheConstants.L2_USER_PERMISSIONS
        );

        postListCache = new MultiLevelCache<>(
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(CacheConstants.L1_POST_LIST, TimeUnit.SECONDS)
                        .build(),
                stringRedisTemplate,
                CacheConstants.POST_LIST,
                CacheConstants.L2_POST_LIST
        );

        sysConfigCache = new MultiLevelCache<>(
                Caffeine.newBuilder()
                        .maximumSize(20)
                        .expireAfterWrite(CacheConstants.L1_SYSTEM_CONFIG, TimeUnit.SECONDS)
                        .build(),
                stringRedisTemplate,
                CacheConstants.SYSTEM_CONFIG,
                CacheConstants.L2_SYSTEM_CONFIG
        );
    }
}
