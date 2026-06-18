package hk.ljx.fishpicsbackend.common.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.infra.JwtUtils;
import hk.ljx.fishpicsbackend.common.utils.PermissionUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.user.entity.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TokenRefreshInterceptor implements HandlerInterceptor {

    private static final String NEW_TOKEN_HEADER = "X-New-Token";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final SpaceTeamMemberMapper spaceTeamMemberMapper;
    private final RedisCacheManager cacheManager;

    public TokenRefreshInterceptor(StringRedisTemplate stringRedisTemplate, JwtUtils jwtUtils,
                                   UserMapper userMapper, SpaceTeamMemberMapper spaceTeamMemberMapper,
                                   RedisCacheManager cacheManager) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
        this.spaceTeamMemberMapper = spaceTeamMemberMapper;
        this.cacheManager = cacheManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String jwt = resolveJwt(request);
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

        if (isTokenInvalidatedSafe(userId, jwt)) {
            writeUnauthorized(response, "登录状态已失效，请重新登录");
            return false;
        }

        if (isBannedSafe(userId)) {
            writeUnauthorized(response, "账号已被封禁");
            return false;
        }

        LoginContext loginContext = loadLoginContext(userId);
        if (loginContext == null) {
            writeUnauthorized(response, "用户不存在，请重新登录");
            return false;
        }

        if (!ExcUtils.eq(loginContext.getStatus(), 1)) {
            writeUnauthorized(response, "账号已被禁用");
            return false;
        }

        renewTokenIfNeeded(userId, jwt, response);
        UserHolder.setLoginContext(loginContext);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeLoginContext();
    }

    private String resolveJwt(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request.getRequestURI().contains("/result-sse/")) {
            return request.getParameter("token");
        }
        return null;
    }

    private boolean isTokenInvalidatedSafe(Long userId, String jwt) {
        try {
            return isTokenInvalidated(userId, jwt);
        } catch (Exception e) {
            log.warn("[TokenRefresh] check token invalidation failed, allow request: userId={}, err={}",
                    userId, e.getMessage());
            return false;
        }
    }

    private boolean isBannedSafe(Long userId) {
        try {
            Boolean banned = stringRedisTemplate.opsForSet()
                    .isMember(RedisConstants.BANNED_USERS_KEY, userId.toString());
            return Boolean.TRUE.equals(banned);
        } catch (Exception e) {
            log.warn("[TokenRefresh] check banned user failed, allow request: userId={}, err={}",
                    userId, e.getMessage());
            return false;
        }
    }

    private void renewTokenIfNeeded(Long userId, String jwt, HttpServletResponse response) {
        if (!jwtUtils.shouldRenew(jwt)) {
            return;
        }

        String renewLockKey = "TOKEN_RENEW:" + userId;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(renewLockKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }

        String newToken = jwtUtils.sign(userId);
        response.setHeader(NEW_TOKEN_HEADER, newToken);
    }

    private LoginContext loadLoginContext(Long userId) {
        String cacheKey = String.valueOf(userId);
        LoginContext cached = cacheManager.getUserPermCache().get(cacheKey, LoginContext.class);
        if (cached != null) {
            return cached;
        }

        User user = getUserFromCacheOrDb(userId);
        if (user == null) {
            return null;
        }

        LoginContext loginContext = PermissionUtils.buildLoginContext(user, loadTeamMemberships(userId));
        cacheManager.getUserPermCache().put(cacheKey, loginContext);
        return loginContext;
    }

    private List<SpaceTeamMember> loadTeamMemberships(Long userId) {
        try {
            return spaceTeamMemberMapper.selectList(
                    new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getUserId, userId));
        } catch (Exception e) {
            log.warn("[TokenRefresh] load team membership failed, fallback to empty permissions: userId={}", userId, e);
            return List.of();
        }
    }

    private User getUserFromCacheOrDb(Long userId) {
        String cacheKey = String.valueOf(userId);
        User cached = cacheManager.getUserInfoCache().get(cacheKey, User.class);
        if (cached != null) {
            return cached;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        user.setPassword(null);
        user.setEmail(null);
        user.setPhone(null);
        cacheManager.getUserInfoCache().put(cacheKey, user);
        return user;
    }

    private boolean isTokenInvalidated(Long userId, String jwt) {
        String invalidBeforeValue = stringRedisTemplate.opsForValue()
                .get(RedisConstants.getUserTokenInvalidBeforeKey(userId));
        if (StrUtil.isBlank(invalidBeforeValue)) {
            return false;
        }

        Claims claims = jwtUtils.parse(jwt);
        if (claims == null || claims.getIssuedAt() == null) {
            return true;
        }

        try {
            long invalidBefore = Long.parseLong(invalidBeforeValue);
            return getIssuedAtMillis(claims) <= invalidBefore;
        } catch (NumberFormatException e) {
            log.error("USER_TOKEN_INVALID_BEFORE value is broken, reject token: userId={}, value={}",
                    userId, invalidBeforeValue);
            return true;
        }
    }

    private long getIssuedAtMillis(Claims claims) {
        Object issuedAtMs = claims.get(JwtUtils.ISSUED_AT_MS_CLAIM);
        if (issuedAtMs instanceof Number number) {
            return number.longValue();
        }
        if (issuedAtMs instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return claims.getIssuedAt().getTime();
            }
        }
        return claims.getIssuedAt().getTime();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(
                new Response<>(ExceptionCode.NOT_LOGIN.getCode(), message, null)
        ));
    }
}
