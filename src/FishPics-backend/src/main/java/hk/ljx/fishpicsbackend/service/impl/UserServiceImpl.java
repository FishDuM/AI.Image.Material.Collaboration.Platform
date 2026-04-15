package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
* @author 30574
* @description 针对表【user(用户表)】的数据库操作Service实现
* @createDate 2026-04-13 21:24:26
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 系统内获取当前登录用户
     *
     * @param request request
     * @return 用户实体
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        ExcUtils.throwIfTrue(authorization == null || !authorization.startsWith("Bearer "), "未登录");
        String user = stringRedisTemplate.opsForValue().get(authorization);
        return JSONUtil.toBean(user, User.class);
    }
}




