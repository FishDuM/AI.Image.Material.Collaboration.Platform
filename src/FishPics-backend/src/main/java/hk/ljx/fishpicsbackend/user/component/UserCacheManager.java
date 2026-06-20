package hk.ljx.fishpicsbackend.user.component;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.utils.PermissionUtils;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class UserCacheManager {

    @Resource
    private RedisCacheManager cacheManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    public void cacheLoginContext(User user) {
        List<SpaceTeamMember> teamMembers = Collections.emptyList();
        try {
            teamMembers = spaceTeamMemberMapper.selectList(
                    new LambdaQueryWrapper<SpaceTeamMember>()
                            .eq(SpaceTeamMember::getUserId, user.getId()));
        } catch (Exception e) {
            log.error("[UserCacheManager] load team memberships failed, user will have degraded permissions: userId={}", user.getId(), e);
        }
        LoginContext loginContext = PermissionUtils.buildLoginContext(user, teamMembers);
        cacheManager.getUserPermCache().put(String.valueOf(user.getId()), loginContext);
    }

    public void refreshUserInfoCache(User user) {
        User cacheUser = new User();
        BeanUtil.copyProperties(user, cacheUser, "password", "email", "phone");
        cacheManager.getUserInfoCache().evict(String.valueOf(user.getId()));
        cacheManager.getUserInfoCache().put(String.valueOf(user.getId()), cacheUser);
    }

    public void evictUserLoginContext(Long userId) {
        cacheManager.getUserPermCache().evict(String.valueOf(userId));
    }

    public void invalidateUserTokens(Long userId) {
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserTokenInvalidBeforeKey(userId),
                String.valueOf(System.currentTimeMillis()),
                RedisConstants.USER_TOKEN_INVALID_TTL_DAYS,
                TimeUnit.DAYS
        );
        evictUserLoginContext(userId);
    }
}
