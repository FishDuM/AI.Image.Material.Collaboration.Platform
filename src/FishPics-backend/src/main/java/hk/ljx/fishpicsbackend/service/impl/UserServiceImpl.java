package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.constants.UserConstants;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.user.UserLoginRequest;
import hk.ljx.fishpicsbackend.dto.user.UserRequestRequest;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.*;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.DEFAULT_NICK_NAME;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.LOGIN_TOKEN;

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
    public String getCheckCode(String str, Integer len, Integer minute) {

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

        // 3. 将图片转为 base64
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            captcha.write(outputStream);
        } catch (Exception e) {
            log.error("验证码转 base64 失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "验证码生成失败");
        }

        byte[] imageBytes = outputStream.toByteArray();

        return Base64.encode(imageBytes);
    }

    /**
     * 系统内获取当前登录用户
     *
     * @param id request
     * @return 用户实体
     */
    @Override
    public User getLoginUser(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public Response<Boolean> userRegister(UserRequestRequest userRequestRequest, HttpServletRequest request) {
        // 基础参数校验
        String username = userRequestRequest.getUsername();
        String password = userRequestRequest.getPassword();
        String checkPassword = userRequestRequest.getCheckPassword();
        String checkCode = userRequestRequest.getCheckCode();
        String captchaKey = userRequestRequest.getCaptchaKey();

        ExcUtils.throwIfTrue(checkCode == null || captchaKey == null, ExceptionCode.PARAMETER_ERROR, "验证码不能为空");
        ExcUtils.throwIfTrue(username == null || username.length() < 6 || username.length() > 11, ExceptionCode.PARAMETER_ERROR, "用户名长度不能小于 6 位或大于 11 位");
        ExcUtils.throwIfTrue(password == null || password.length() < 8 || password.length() > 20, ExceptionCode.PARAMETER_ERROR, "密码长度不能小于 8 位或大于 20 位");
        if (password != null) {
            ExcUtils.throwIfTrue(!password.equals(checkPassword), ExceptionCode.PARAMETER_ERROR, "两次密码不一致");
        }

        // 校验验证码
        String checkCodeKeyByRegister = RedisConstants.getRegisterCodeKey(captchaKey);
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

    @Override
    public Response<UserLoginVO> userLogin(UserLoginRequest userLoginRequest, HttpServletResponse response, HttpServletRequest request) {
        String username = userLoginRequest.getUsername();
        String password = userLoginRequest.getPassword();
        String checkCode = userLoginRequest.getCheckCode();
        String captchaKey = userLoginRequest.getCaptchaKey();
        ExcUtils.throwIfTrue(StrUtil.isAllBlank(username, password, checkCode, captchaKey), ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        // 校验验证码
        String checkCodeKeyByLogin = RedisConstants.getLoginCodeKey(captchaKey);
        String cacheCode = stringRedisTemplate.opsForValue().get(checkCodeKeyByLogin);
        ExcUtils.throwIfTrue(cacheCode == null || !cacheCode.equalsIgnoreCase(checkCode), ExceptionCode.PARAMETER_ERROR, "验证码错误");

        // 查询 mysql 获取用户
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username).eq("password", password));
        ExcUtils.throwIfTrue(user == null, ExceptionCode.PARAMETER_ERROR, "用户名或密码错误");
        String userByJson = JSONUtil.toJsonStr(user);

        // 查询到则存入 Redis
        String loginTokenKey = null;
        if (user != null) {
            loginTokenKey = UserConstants.getLoginTokenKey(user.getId());
        } else {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "用户名或密码错误");
        }
        stringRedisTemplate.opsForValue().set(loginTokenKey, userByJson, 1, TimeUnit.DAYS);

        response.setHeader("Authorization", loginTokenKey);
        response.setHeader("Access-Control-Expose-Headers", "Authorization");
        // 查到用户数据返回封装类
        UserLoginVO userLoginVO = BeanUtil.copyProperties(user, UserLoginVO.class);

        return ResUtils.success(userLoginVO);
    }
}




