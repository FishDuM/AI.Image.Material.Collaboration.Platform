package hk.ljx.fishpicsbackend.common.cache;

import hk.ljx.fishpicsbackend.common.constants.CacheConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RedisCacheManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private RedisTtlCache userInfoCache;
    private RedisTtlCache userPermCache;
    private RedisTtlCache sysConfigCache;
    private RedisTtlCache spaceDetailCache;
    private RedisTtlCache teamMemberCache;
    private RedisTtlCache shareCache;

    public RedisTtlCache getUserInfoCache() {
        return userInfoCache;
    }

    public RedisTtlCache getUserPermCache() {
        return userPermCache;
    }

    public RedisTtlCache getSysConfigCache() {
        return sysConfigCache;
    }

    public RedisTtlCache getSpaceDetailCache() {
        return spaceDetailCache;
    }

    public RedisTtlCache getTeamMemberCache() {
        return teamMemberCache;
    }

    public RedisTtlCache getShareCache() {
        return shareCache;
    }

    @PostConstruct
    public void init() {
        userInfoCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.USER_INFO, CacheConstants.USER_INFO_TTL_MINUTES);
        userPermCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.USER_PERMISSIONS, CacheConstants.USER_PERMISSIONS_TTL_MINUTES);
        sysConfigCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.SYSTEM_CONFIG, CacheConstants.SYSTEM_CONFIG_TTL_MINUTES);
        spaceDetailCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.SPACE_DETAIL, CacheConstants.SPACE_DETAIL_TTL_MINUTES);
        teamMemberCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.TEAM_MEMBER, CacheConstants.TEAM_MEMBER_TTL_MINUTES);
        shareCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.SHARE, CacheConstants.SHARE_TTL_MINUTES);
    }

    /**
     * 事务提交后清除用户权限缓存（公共方法，消除跨类重复代码）
     */
    public void evictUserPermCacheAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        Runnable evict = () -> userPermCache.evict(String.valueOf(userId));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict.run();
            }
        });
    }
}
