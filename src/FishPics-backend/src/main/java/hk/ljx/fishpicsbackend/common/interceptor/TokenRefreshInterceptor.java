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
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * Token refresh interceptor.
 */
public class TokenRefreshInterceptor implements HandlerInterceptor {

    private static final String NEW_TOKEN_HEADER = "X-New-Token";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    public TokenRefreshInterceptor(StringRedisTemplate stringRedisTemplate, JwtUtils jwtUtils, UserMapper userMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtils = jwtUtils;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            return true;
        }

        String jwt = authHeader.substring(7);
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

        if (isTokenInvalidated(userId, jwt)) {
            writeUnauthorized(response, "登录状态已失效，请重新登录");
            return false;
        }

        Boolean isBanned = stringRedisTemplate.opsForSet().isMember(RedisConstants.BANNED_USERS_KEY, userId.toString());
        if (Boolean.TRUE.equals(isBanned)) {
            writeUnauthorized(response, "账号已被封禁");
            return false;
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
            response.setHeader(NEW_TOKEN_HEADER, jwtUtils.sign(userId));
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
        String ctxJson = stringRedisTemplate.opsForValue().get(ctxKey);
        if (StrUtil.isNotBlank(ctxJson)) {
            return JSONUtil.toBean(ctxJson, LoginContext.class);
        }

        User user = getUserFromCacheOrDb(userId);
        if (user == null) {
            return null;
        }

        LoginContext loginContext = PermissionUtils.buildLoginContext(user);
        stringRedisTemplate.opsForValue().set(
                ctxKey,
                JSONUtil.toJsonStr(loginContext),
                RedisConstants.USER_PERM_CTX_TTL,
                TimeUnit.DAYS
        );
        return loginContext;
    }

    private User getUserFromCacheOrDb(Long userId) {
        String userKey = RedisConstants.getUserInfoKey(userId);
        String userJson = stringRedisTemplate.opsForValue().get(userKey);
        if (StrUtil.isNotBlank(userJson)) {
            return JSONUtil.toBean(userJson, User.class);
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
            return false;
        }
    }

    private void refreshContextTtlIfNeeded(Long userId) {
        String ctxKey = RedisConstants.getUserPermCtxKey(userId);
        Long ttl = stringRedisTemplate.getExpire(ctxKey, TimeUnit.DAYS);
        if (ttl != null && ttl < RedisConstants.USER_PERM_CTX_TTL / 2) {
            stringRedisTemplate.expire(ctxKey, RedisConstants.USER_PERM_CTX_TTL, TimeUnit.DAYS);
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
