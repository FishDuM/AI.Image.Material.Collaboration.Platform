package hk.ljx.fishpicsbackend.common.cache;

import hk.ljx.fishpicsbackend.common.constants.CacheConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCacheManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private RedisTtlCache userInfoCache;
    private RedisTtlCache userPermCache;
    private RedisTtlCache sysConfigCache;

    public RedisTtlCache getUserInfoCache() {
        return userInfoCache;
    }

    public RedisTtlCache getUserPermCache() {
        return userPermCache;
    }

    public RedisTtlCache getSysConfigCache() {
        return sysConfigCache;
    }

    @PostConstruct
    public void init() {
        userInfoCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.USER_INFO, CacheConstants.USER_INFO_TTL_MINUTES);
        userPermCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.USER_PERMISSIONS, CacheConstants.USER_PERMISSIONS_TTL_MINUTES);
        sysConfigCache = new RedisTtlCache(stringRedisTemplate, CacheConstants.SYSTEM_CONFIG, CacheConstants.SYSTEM_CONFIG_TTL_MINUTES);
    }
}
