package hk.ljx.fishpicsbackend.common.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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

    RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 基于redis获取token中的用户
        String tokenKey = RedisConstants.getUserIdKey(token);
        String userIdKey = stringRedisTemplate.opsForValue().get(tokenKey);
        User user = null;
        String userKey = null;
        if (userIdKey != null) {
            userKey = RedisConstants.getUserInfoKey(Long.parseLong(userIdKey));
        }
        String userJSon = null;
        if (userKey != null) {
            userJSon = stringRedisTemplate.opsForValue().get(userKey);
        }
        if (StrUtil.isNotBlank(userJSon)) {
            user = JSONUtil.toBean(userJSon, User.class);
        }
        if (user != null) {
            // 将用户信息存入线程中并刷新redis有效期
            UserHolder.setUser(user);
            stringRedisTemplate.expire(tokenKey, 1, TimeUnit.DAYS);
            stringRedisTemplate.expire(userKey, 1, TimeUnit.DAYS);
            return true;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
