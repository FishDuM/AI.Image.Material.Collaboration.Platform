package hk.ljx.fishpicsbackend.common.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final MultiLevelCacheManager cacheManager;

    RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate, MultiLevelCacheManager cacheManager){
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheManager = cacheManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 基于redis获取token中的用户
        String tokenKey = RedisConstants.getUserIdKey(token);
        String userIdStr = stringRedisTemplate.opsForValue().get(tokenKey);
        if (userIdStr == null) {
            return true;
        }

        Long userId = Long.parseLong(userIdStr);

        // 多级缓存拿用户信息，都没有就回退到直接读Redis
        User user = null;
        Object cached = cacheManager.userInfoCache.get(String.valueOf(userId));
        if (cached instanceof User u) {
            user = u;
        } else if (cached instanceof JSONObject json) {
            // Redis里拿出来的是JSONObject，转成User对象
            user = json.toBean(User.class);
        } else {
            // 缓存没命中，去Redis里找登录时写入的用户信息
            String userKey = RedisConstants.getUserInfoKey(userId);
            String userJson = stringRedisTemplate.opsForValue().get(userKey);
            if (StrUtil.isNotBlank(userJson)) {
                user = JSONUtil.toBean(userJson, User.class);
                // 写进缓存下次就不用再查Redis了
                cacheManager.userInfoCache.put(String.valueOf(userId), user);
            }
        }

        if (user != null) {
            // 检查用户状态，禁用用户不允许访问
            if (user.getStatus() != null && user.getStatus() == 0) {
                // 用户已被禁用，清除Token和L1缓存
                stringRedisTemplate.delete(tokenKey);
                cacheManager.userInfoCache.evict(String.valueOf(userId));
                return false;
            }
            // 将用户信息存入线程中并刷新redis有效期
            UserHolder.setUser(user);
            stringRedisTemplate.expire(tokenKey, 1, TimeUnit.DAYS);
            stringRedisTemplate.expire(RedisConstants.getUserInfoKey(userId), 1, TimeUnit.DAYS);
            return true;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
