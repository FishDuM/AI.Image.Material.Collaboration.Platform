package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.captcha.ICaptcha;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.user.UserRequestRequest;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.DEFAULT_NICK_NAME;

/**
* @author 30574
* @description 针对表【user(用户表)】的数据库操作Service实现
* @createDate 2026-04-13 21:24:26
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Override
    public void getCheckCode(String str, Integer len, Integer minute, HttpServletResponse response) {

        // 如果没有传验证码长度或过期时间则用默认值
        if (len == null) {
            len = 4;
        }

        if (minute == null) {
            minute = 5;
        }

        // 1. 创建圆圈验证码
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(200, 100, len, 20);

        // 2. 验证码 code 存到 redis
        String code = captcha.getCode();
        stringRedisTemplate.opsForValue().set(str, code, minute, TimeUnit.MINUTES);

        // 3. 响应头设置（必须加，否则浏览器不识别图片）
        response.setContentType("image/png");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        // 4. 输出图片 + 刷新 + 关闭流
        try (ServletOutputStream out = response.getOutputStream()) {
            captcha.write(out);
            out.flush(); // 强制把缓冲区数据发送给浏览器
        } catch (IOException e) {
            // 异常处理
            log.error("验证码生成失败", e);
        }

    }

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

    @Override
    public Response<Boolean> userRegister(UserRequestRequest userRequestRequest, HttpServletRequest request) {
        // 基础参数校验
        String username = userRequestRequest.getUsername();
        String password = userRequestRequest.getPassword();
        String checkPassword = userRequestRequest.getCheckPassword();
        String checkCode = userRequestRequest.getCheckCode();

        ExcUtils.throwIfTrue(checkCode == null, ExceptionCode.PARAMETER_ERROR, "验证码不能为空");
        ExcUtils.throwIfTrue(username == null || username.length() < 6 || username.length() > 11, ExceptionCode.PARAMETER_ERROR, "用户名长度不能小于6位或大于11位");
        ExcUtils.throwIfTrue(password == null || password.length() < 8 || password.length() > 20, ExceptionCode.PARAMETER_ERROR, "密码长度不能小于8位或大于20位");
        if (password != null) {
            ExcUtils.throwIfTrue(!password.equals(checkPassword), ExceptionCode.PARAMETER_ERROR, "两次密码不一致");
        }

        // 获取请求头的注册 key
        String registerKey =(String) request.getSession().getAttribute("register");

        // 校验验证码
        String checkCodeKeyByRegister = RedisConstants.getCheckCodeKeyByRegister(registerKey);
        String code = stringRedisTemplate.opsForValue().get(checkCodeKeyByRegister);
        ExcUtils.throwIfTrue(checkCode == null || !checkCode.equalsIgnoreCase(code), ExceptionCode.PARAMETER_ERROR, "验证码错误");

        // 校验用户名是否已存在
        Long num = userMapper.selectCount(new QueryWrapper<User>().eq("username", username));
        ExcUtils.throwIfTrue(num != 0, ExceptionCode.PARAMETER_ERROR, "用户名已存在");

        // 注册用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(DEFAULT_NICK_NAME + RandomUtil.randomString(6));

        int insert = userMapper.insert(user);
        ExcUtils.throwIfTrue(insert != 1, ExceptionCode.DATABASE_ERROR, "注册失败");
        stringRedisTemplate.delete(checkCodeKeyByRegister);
        return ResUtils.success(true);
    }
}




