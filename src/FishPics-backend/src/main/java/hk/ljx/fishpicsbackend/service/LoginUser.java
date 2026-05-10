package hk.ljx.fishpicsbackend.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.TOKEN_KEY;
import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.getUserIdKey;

@Component
public class LoginUser {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public User getLoginUser(HttpServletRequest request) {
        // session 查询用户信息
        Object attribute = request.getSession().getAttribute(TOKEN_KEY);
        Long userId = null;
        if (attribute != null){
            userId = Long.parseLong(attribute.toString());
        }
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userId), ExceptionCode.UNAUTHORIZED, "用户未登录");
        // 查询 redis 获取用户信息
        String userJson = stringRedisTemplate.opsForValue().get(getUserIdKey(userId));
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userJson), ExceptionCode.UNAUTHORIZED, "用户未登录");
        return JSONUtil.toBean(userJson, User.class);
    }
}
