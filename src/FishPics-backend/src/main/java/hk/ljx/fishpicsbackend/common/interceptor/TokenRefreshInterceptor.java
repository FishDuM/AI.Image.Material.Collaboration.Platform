package hk.ljx.fishpicsbackend.common.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.utils.JwtUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.permission.service.PermissionService;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * Token 刷新拦截器（新版）
 * JWT 解析 + 黑名单检查 + 自动续签 + 权限上下文加载
 *
 * 执行顺序：order=0（最先执行）
 */
public class TokenRefreshInterceptor implements HandlerInterceptor {

    private static final String NEW_TOKEN_HEADER = "X-New-Token";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;
    private final PermissionService permissionService;

    public TokenRefreshInterceptor(StringRedisTemplate stringRedisTemplate,
                                    JwtUtils jwtUtils,
                                    PermissionService permissionService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtils = jwtUtils;
        this.permissionService = permissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头获取 JWT（严格要求 Bearer 前缀）
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            return true; // 无 Token，放行（由 LoginInterceptor 判断是否需要登录）
        }

        String jwt = authHeader.substring(7);
        if (StrUtil.isBlank(jwt)) {
            return true;
        }

        // 2. 检查黑名单（已登出的 Token）
        if (jwtUtils.isBlacklisted(jwt)) {
            writeUnauthorized(response, "Token 已失效，请重新登录");
            return false;
        }

        // 3. 解析 JWT 获取 userId
        Long userId = jwtUtils.getUserId(jwt);
        if (userId == null) {
            // JWT 无效或过期，尝试检查是否在黑名单
            if (jwtUtils.isExpired(jwt)) {
                writeUnauthorized(response, "登录已过期，请重新登录");
            }
            return true; // 放行，由 LoginInterceptor 拦截
        }

        // 4. 从 Redis 读取权限上下文
        String ctxKey = RedisConstants.getUserPermCtxKey(userId);
        String ctxJson = stringRedisTemplate.opsForValue().get(ctxKey);

        LoginContext loginContext;
        boolean needRefreshTtl = false;

        if (StrUtil.isNotBlank(ctxJson)) {
            // Redis 命中，反序列化
            loginContext = JSONUtil.toBean(ctxJson, LoginContext.class);

            // 检查用户状态
            if (loginContext.getStatus() != null && loginContext.getStatus() == 0) {
                writeUnauthorized(response, "账号已被禁用");
                return false;
            }
        } else {
            // Redis 未命中，从数据库重建
            User user = getUserFromRedis(userId);
            if (user == null) {
                return true; // 用户不存在，放行
            }

            // 检查用户状态
            if (user.getStatus() != null && user.getStatus() == 0) {
                writeUnauthorized(response, "账号已被禁用");
                return false;
            }

            // 从数据库构建权限上下文
            loginContext = permissionService.buildLoginContext(
                    userId, user.getUsername(), user.getNickname(),
                    user.getAvatar(), user.getStatus(), user.getLevel());

            // 写入 Redis
            stringRedisTemplate.opsForValue().set(
                    ctxKey, JSONUtil.toJsonStr(loginContext),
                    RedisConstants.USER_PERM_CTX_TTL, TimeUnit.DAYS);
            needRefreshTtl = false; // 刚写入，不需要刷新
        }

        // 5. 刷新 Redis 会话 TTL（7天滑动续期）- 使用惰性刷新策略
        // 只在 TTL 小于一半时刷新，减少 Redis 操作
        Long ttl = stringRedisTemplate.getExpire(ctxKey, TimeUnit.DAYS);
        if (ttl != null && ttl < RedisConstants.USER_PERM_CTX_TTL / 2) {
            stringRedisTemplate.expire(ctxKey, RedisConstants.USER_PERM_CTX_TTL, TimeUnit.DAYS);
        }

        // 6. JWT 自动续签（超过 15 分钟签发新 JWT）
        if (jwtUtils.shouldRenew(jwt)) {
            String newJwt = jwtUtils.sign(userId);
            response.setHeader(NEW_TOKEN_HEADER, newJwt);
        }

        // 7. 存入 ThreadLocal
        UserHolder.setLoginContext(loginContext);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) throws Exception {
        UserHolder.removeLoginContext();
    }

    /**
     * 从 Redis 获取用户基本信息（登录时写入的 USER_MESSAGE:{userId}）
     */
    private User getUserFromRedis(Long userId) {
        String userKey = RedisConstants.getUserInfoKey(userId);
        String userJson = stringRedisTemplate.opsForValue().get(userKey);
        if (StrUtil.isNotBlank(userJson)) {
            return JSONUtil.toBean(userJson, User.class);
        }
        return null;
    }

    /**
     * 返回 401 响应
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40001,\"message\":\"" + message + "\"}");
    }
}
