package hk.ljx.fishpicsbackend.common.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.JwtUtils;
import hk.ljx.fishpicsbackend.common.utils.PermissionUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.user.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Token refresh interceptor.
 */
@Slf4j
public class TokenRefreshInterceptor implements HandlerInterceptor {

    private static final String NEW_TOKEN_HEADER = "X-New-Token";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final SpaceTeamMemberMapper spaceTeamMemberMapper;

    public TokenRefreshInterceptor(StringRedisTemplate stringRedisTemplate, JwtUtils jwtUtils,
                                    UserMapper userMapper, SpaceTeamMemberMapper spaceTeamMemberMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
        this.spaceTeamMemberMapper = spaceTeamMemberMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        String jwt = null;
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }
        // SSE/EventSource 无法设置自定义 Header，支持 query 参数 ?token=xxx
        if (StrUtil.isBlank(jwt)) {
            jwt = request.getParameter("token");
        }
        if (StrUtil.isBlank(jwt)) {
            return true;
        }

        if (jwtUtils.isBlacklisted(jwt)) {
            writeUnauthorized(response, "Token 已失效，请重新登录");
            return false;
        }

        Long userId = jwtUtils.getUserId(jwt);
        if (userId == null) {
            writeUnauthorized(response, jwtUtils.isExpired(jwt) ? "登录已过期，请重新登录" : "Token 无效，请重新登录");
            return false;
        }

        // Redis 故障时 isTokenInvalidated/isBanned 抛异常会 500 让所有用户无法登录
        // 降级:Redis 不可用 → 放行
        try {
            if (isTokenInvalidated(userId, jwt)) {
                writeUnauthorized(response, "登录状态已失效，请重新登录");
                return false;
            }
        } catch (Exception e) {
            log.warn("[TokenRefresh] 检查 token invalidate 失败(Redis 故障,降级放行): userId={}, err={}",
                    userId, e.getMessage());
        }

        try {
            Boolean isBanned = stringRedisTemplate.opsForSet().isMember(RedisConstants.BANNED_USERS_KEY, userId.toString());
            if (Boolean.TRUE.equals(isBanned)) {
                writeUnauthorized(response, "账号已被封禁");
                return false;
            }
        } catch (Exception e) {
            log.warn("[TokenRefresh] 检查 BANNED_USERS 失败(Redis 故障,降级放行): userId={}, err={}",
                    userId, e.getMessage());
        }

        LoginContext loginContext = loadLoginContext(userId);
        if (loginContext == null) {
            writeUnauthorized(response, "用户不存在，请重新登录");
            return false;
        }

        if (!Integer.valueOf(1).equals(loginContext.getStatus())) {
            writeUnauthorized(response, "账号已被禁用");
            return false;
        }

        refreshContextTtlIfNeeded(userId);

        if (jwtUtils.shouldRenew(jwt)) {
            // 用 Redis setIfAbsent 确保并发请求中只有一个执行续签
            String renewLockKey = "TOKEN_RENEW:" + userId;
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(renewLockKey, "1", 30, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(acquired)) {
                // 先生成新 token 写入响应头,再黑名单旧 token
                // 避免先黑名单后网络中断导致用户既没有旧 token 也没有新 token
                String newToken = jwtUtils.sign(userId);
                response.setHeader(NEW_TOKEN_HEADER, newToken);
                // addToBlacklist 失败时旧 token 自然过期
                try {
                    jwtUtils.addToBlacklist(jwt);
                } catch (Exception redisEx) {
                    log.warn("[TokenRefresh] addToBlacklist 失败(旧 token 将自然过期): userId={}, err={}",
                            userId, redisEx.getMessage());
                }
            }
        }

        UserHolder.setLoginContext(loginContext);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeLoginContext();
    }

    private LoginContext loadLoginContext(Long userId) {
        String ctxKey = RedisConstants.getUserPermCtxKey(userId);
        String ctxJson;
        try {
            ctxJson = stringRedisTemplate.opsForValue().get(ctxKey);
        } catch (Exception e) {
            log.warn("[TokenRefresh] Redis 读取 LoginContext 失败(降级查DB): userId={}, err={}", userId, e.getMessage());
            ctxJson = null;
        }
        if (StrUtil.isNotBlank(ctxJson)) {
            // 反序列化失败时降级为重新构建
            try {
                return JSONUtil.toBean(ctxJson, LoginContext.class);
            } catch (Exception e) {
                log.warn("[TokenRefresh] Redis LoginContext 反序列化失败,降级为重新构建: userId={}, err={}",
                        userId, e.getMessage());
                stringRedisTemplate.delete(ctxKey); // 删掉损坏的缓存,下次走 DB 重建
            }
        }

        User user = getUserFromCacheOrDb(userId);
        if (user == null) {
            return null;
        }

        LoginContext loginContext = PermissionUtils.buildLoginContext(user, loadTeamMemberships(userId));
        stringRedisTemplate.opsForValue().set(
                ctxKey,
                JSONUtil.toJsonStr(loginContext),
                RedisConstants.USER_PERM_CTX_TTL,
                TimeUnit.DAYS
        );
        return loginContext;
    }

    private List<SpaceTeamMember> loadTeamMemberships(Long userId) {
        if (spaceTeamMemberMapper == null) return List.of();
        try {
            return spaceTeamMemberMapper.selectList(
                    new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getUserId, userId));
        } catch (Exception e) {
            // 团队权限是辅助信息,加载失败不应阻塞请求,降级为无团队权限
            log.warn("[TokenRefresh] 加载团队成员关系失败: userId={}", userId, e);
            return List.of();
        }
    }

    private User getUserFromCacheOrDb(Long userId) {
        String userKey = RedisConstants.getUserInfoKey(userId);
        String userJson;
        try {
            userJson = stringRedisTemplate.opsForValue().get(userKey);
        } catch (Exception e) {
            log.warn("[TokenRefresh] Redis 读取 User 缓存失败(降级查DB): userId={}, err={}", userId, e.getMessage());
            userJson = null;
        }
        if (StrUtil.isNotBlank(userJson)) {
            // Redis User JSON 反序列化失败时降级查 DB
            try {
                return JSONUtil.toBean(userJson, User.class);
            } catch (Exception e) {
                log.warn("[TokenRefresh] Redis User 反序列化失败,降级查 DB: userId={}, err={}",
                        userId, e.getMessage());
                stringRedisTemplate.delete(userKey);
            }
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        User cacheUser = new User();
        BeanUtil.copyProperties(user, cacheUser, "password", "email", "phone");
        stringRedisTemplate.opsForValue().set(
                userKey,
                JSONUtil.toJsonStr(cacheUser),
                RedisConstants.USER_PERM_CTX_TTL,
                TimeUnit.DAYS
        );
        return user;
    }

    private boolean isTokenInvalidated(Long userId, String jwt) {
        String invalidBeforeValue = stringRedisTemplate.opsForValue().get(RedisConstants.getUserTokenInvalidBeforeKey(userId));
        if (StrUtil.isBlank(invalidBeforeValue)) {
            return false;
        }

        Claims claims = jwtUtils.parse(jwt);
        if (claims == null || claims.getIssuedAt() == null) {
            return true;
        }

        try {
            long invalidBefore = Long.parseLong(invalidBeforeValue);
            return claims.getIssuedAt().getTime() <= invalidBefore;
        } catch (NumberFormatException e) {
            // 安全默认值反转 — Redis 中 token invalidation 时间值损坏时，宁可拒绝 token 也不放行
            log.error("USER_TOKEN_INVALID_BEFORE 值损坏,默认拒绝该 token: userId={}, value={}",
                    userId, invalidBeforeValue);
            return true;
        }
    }

    private void refreshContextTtlIfNeeded(Long userId) {
        try {
            String ctxKey = RedisConstants.getUserPermCtxKey(userId);
            Long ttl = stringRedisTemplate.getExpire(ctxKey, TimeUnit.DAYS);
            if (ttl != null && ttl < RedisConstants.USER_PERM_CTX_TTL / 2) {
                stringRedisTemplate.expire(ctxKey, RedisConstants.USER_PERM_CTX_TTL, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.warn("[TokenRefresh] 刷新 TTL 失败(Redis 故障,非阻塞): userId={}, err={}", userId, e.getMessage());
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(
                new Response<>(ExceptionCode.NOT_LOGIN.getCode(), message, null)
        ));
    }
}
