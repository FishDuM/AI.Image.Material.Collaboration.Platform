package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.dto.user.*;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.service.LoginUser;
import hk.ljx.fishpicsbackend.service.SpaceService;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.vo.user.UserLoginVO;
import hk.ljx.fishpicsbackend.vo.user.UserMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.*;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.DEFAULT_NICK_NAME;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.SALT;

/**
 * @author 30574
 *         针对表【user(用户表)】的数据库操作Service实现
 *         2026-04-13 21:24:26
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceService spaceService;

    @Resource
    private LoginUser loginUser;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> userRegister(UserRequestRequest userRequestRequest, HttpServletRequest request) {
        // 基础参数校验
        String username = userRequestRequest.getUsername();
        String password = userRequestRequest.getPassword();
        String checkPassword = userRequestRequest.getCheckPassword();
        String checkCode = userRequestRequest.getCheckCode();
        String captchaKey = userRequestRequest.getCaptchaKey();

        ExcUtils.throwIfTrue(checkCode == null || captchaKey == null, ExceptionCode.PARAMETER_ERROR, "验证码不能为空");
        ExcUtils.throwIfTrue(username == null || username.length() < 6 || username.length() > 11,
                ExceptionCode.PARAMETER_ERROR, "账号长度不能小于 6 位或大于 11 位");
        ExcUtils.throwIfTrue(password == null || password.length() < 8 || password.length() > 20,
                ExceptionCode.PARAMETER_ERROR, "密码长度不能小于 8 位或大于 20 位");
        if (password != null) {
            ExcUtils.throwIfTrue(!password.equals(checkPassword), ExceptionCode.PARAMETER_ERROR, "两次密码不一致");
        }

        // 校验验证码
        String checkCodeKeyByRegister = RedisConstants.getRegisterCodeKey(captchaKey);
        String code = stringRedisTemplate.opsForValue().get(checkCodeKeyByRegister);
        ExcUtils.throwIfTrue(checkCode == null || !checkCode.equalsIgnoreCase(code), ExceptionCode.PARAMETER_ERROR,
                "验证码错误");

        // 校验账号是否已存在
        Long num = userMapper.selectCount(new QueryWrapper<User>().eq("username", username));
        ExcUtils.throwIfTrue(num != 0, ExceptionCode.PARAMETER_ERROR, "账号已存在");

        // 密码加盐
        password = DigestUtil.md5Hex(password + SALT);

        // 注册用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setLevel(0);
        user.setNickname(DEFAULT_NICK_NAME + RandomUtil.randomString(6));
        // 默认头像
        user.setAvatar("https://avatars.githubusercontent.com/u/179127403?v=4");
        int insert = userMapper.insert(user);
        ExcUtils.throwIfTrue(insert != 1, ExceptionCode.DATABASE_ERROR, "注册失败");
        stringRedisTemplate.delete(checkCodeKeyByRegister);
        // 创建默认私人空间
        Boolean spaceRequest = spaceService.createSpace(new CreateSpace(user.getNickname() + "的私人空间", "你的专属私密存储空间", 0), user);
        ExcUtils.throwIfTrue(!spaceRequest, ExceptionCode.DATABASE_ERROR, "创建私人空间失败");
        return ResUtils.success(true);
    }

    @Override
    public Response<UserLoginVO> userLogin(UserLoginRequest userLoginRequest,
            HttpServletRequest request) {
        String username = userLoginRequest.getUsername();
        String password = userLoginRequest.getPassword();
        String checkCode = userLoginRequest.getCheckCode();
        String captchaKey = userLoginRequest.getCaptchaKey();
        ExcUtils.throwIfTrue(StrUtil.isAllBlank(username, password, checkCode, captchaKey),
                ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        // 密码加盐
        password = DigestUtil.md5Hex(password + SALT);

        // 校验验证码
        String checkCodeKeyByLogin = RedisConstants.getLoginCodeKey(captchaKey);
        String cacheCode = stringRedisTemplate.opsForValue().get(checkCodeKeyByLogin);
        ExcUtils.throwIfTrue(cacheCode == null || !cacheCode.equalsIgnoreCase(checkCode), ExceptionCode.PARAMETER_ERROR,
                "验证码错误");

        // 查询 mysql 获取用户
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username).eq("password", password));
        ExcUtils.throwIfTrue(user == null, ExceptionCode.PARAMETER_ERROR, "账号或密码错误");

        ExcUtils.throwIfTrue(user.getStatus() == null || user.getStatus() != 1, ExceptionCode.PARAMETER_ERROR, "账号已被禁用");

        // 查询到则存入 session
        Long id = user.getId();
        request.getSession().setAttribute(TOKEN_KEY, id);
        stringRedisTemplate.opsForValue().set(getUserIdKey(id), JSONUtil.toJsonStr(user), 1, TimeUnit.DAYS);

        // 查到用户数据返回封装类
        UserLoginVO userLoginVO = BeanUtil.copyProperties(user, UserLoginVO.class);
        return ResUtils.success(userLoginVO);
    }

    @Override
    public QueryWrapper<User> newQueryWrapper(UserQueryWrapper userQueryWrapper) {
        Long id = userQueryWrapper.getId();
        String username = userQueryWrapper.getUsername();
        String email = userQueryWrapper.getEmail();
        String phone = userQueryWrapper.getPhone();
        String nickname = userQueryWrapper.getNickname();
        String role = userQueryWrapper.getRole();
        Integer status = userQueryWrapper.getStatus();
        Date createTime = userQueryWrapper.getCreateTime();
        String sortField = userQueryWrapper.getSortField();
        String sortOrder = userQueryWrapper.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjectUtil.isNotNull(id), "id", id);
        queryWrapper.like(ObjectUtil.isNotNull(username), "username", username);
        queryWrapper.like(ObjectUtil.isNotNull(email), "email", email);
        queryWrapper.like(ObjectUtil.isNotNull(phone), "phone", phone);
        queryWrapper.like(ObjectUtil.isNotNull(nickname), "nickname", nickname);
        queryWrapper.eq(ObjectUtil.isNotNull(role), "role", role);
        queryWrapper.eq(ObjectUtil.isNotNull(status), "status", status);
        queryWrapper.eq(ObjectUtil.isNotNull(createTime), "create_time", createTime);

        queryWrapper.orderBy(ObjectUtil.isNotNull(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public IPage<User> getUserList(UserQueryWrapper userQueryWrapper, long current, long pageSize) {
        ExcUtils.throwIfTrue(current <= 0 || pageSize <= 0, ExceptionCode.PARAMETER_ERROR);
        QueryWrapper<User> queryWrapper = this.newQueryWrapper(userQueryWrapper);
        return userMapper.selectPage(new Page<>(current, pageSize), queryWrapper);
    }

    @Override
    public Boolean setStatus(Long userId) {
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isNull(user), ExceptionCode.NOT_FOUND, "未找到该用户");
        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        int i = userMapper.updateById(user);
        stringRedisTemplate.opsForValue().set(getUserIdKey(userId), JSONUtil.toJsonStr(user), 1, TimeUnit.DAYS);
        return i > 0;
    }

    @Override
    public Boolean editUser(@RequestBody UserEditByAdminRequest userEditByAdminRequest) {
        // 1. 必传校验
        Long id = userEditByAdminRequest.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "用户ID不能为空");

        // 2. 查询用户是否存在
        User user = userMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isNull(user), ExceptionCode.NOT_FOUND, "未找到该用户");

        // 3. 密码加密（只在密码不为空时加密）
        if (ObjectUtil.isNotEmpty(userEditByAdminRequest.getPassword())) {
            String encryptPwd = DigestUtil.md5Hex(userEditByAdminRequest.getPassword() + SALT);
            userEditByAdminRequest.setPassword(encryptPwd);
        }

        // 4. 自动拷贝 非空字段 到实体类（自动忽略null/空值）
        BeanUtil.copyProperties(userEditByAdminRequest, user,
                CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true));

        // 5. 更新
        int rows = userMapper.updateById(user);
        stringRedisTemplate.opsForValue().set(getUserIdKey(id), JSONUtil.toJsonStr(user), 1, TimeUnit.DAYS);
        ExcUtils.throwIfTrue(rows != 1, ExceptionCode.DATABASE_ERROR, "更新用户失败");

        return true;
    }

    @Override
    public UserMessageVO getMyselfMessage(HttpServletRequest request) {
        User user = loginUser.getLoginUser(request);
        ExcUtils.throwIfTrue(user == null || user.getId() == null,
                ExceptionCode.NOT_FOUND, "未登录");
        UserMessageVO vo = new UserMessageVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    public Boolean editMyself(UserEditRequest userEditRequest, HttpServletRequest request) {
        // 获得当前登录用户信息
        User userLogin = loginUser.getLoginUser(request);
        ExcUtils.throwIfTrue(ObjectUtil.isAllEmpty(userLogin, userLogin.getId()), ExceptionCode.NOT_LOGIN);
        // 参数校验
        Long id = userEditRequest.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR);
        String nickname = userEditRequest.getNickname();
        if (nickname != null) {
            ExcUtils.throwIfTrue(!(nickname.length() > 4 && nickname.length() < 12), ExceptionCode.PARAMETER_ERROR,
                    "昵称长度为5-11位");
        }
        String username = userEditRequest.getUsername();
        if (username != null) {
            ExcUtils.throwIfTrue(!(username.length() > 5 && username.length() < 12), ExceptionCode.PARAMETER_ERROR,
                    "账号长度为6-11位");
        }
        String password = userEditRequest.getPassword();
        if (password != null) {
            ExcUtils.throwIfTrue(!(password.length() > 7 && password.length() < 21), ExceptionCode.PARAMETER_ERROR,
                    "密码长度为8-20位");
        }

        // 校验是否是自己的信息
        ExcUtils.throwIfTrue(!this.isMe(id, request), ExceptionCode.UNAUTHORIZED, "只可修改自己的信息");
        // 查询用户信息
        User user = this.getById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isNull(user), ExceptionCode.DATABASE_ERROR, "用户不存在");

        // 更新用户信息
        BeanUtil.copyProperties(userEditRequest, user, CopyOptions.create().ignoreNullValue());
        if (StrUtil.isNotBlank(password)) {
            // 校验原始密码
            String originalPassword = userEditRequest.getOriginalPassword();
            ExcUtils.throwIfTrue(StrUtil.isEmpty(originalPassword), ExceptionCode.PARAMETER_ERROR, "请输入初始密码");
            user = userMapper.selectById(userLogin.getId());
            String oldPassword = user.getPassword();
            ExcUtils.throwIfTrue(!DigestUtil.md5Hex(originalPassword + SALT).equals(oldPassword), ExceptionCode.PARAMETER_ERROR, "初始密码错误");
            // 密码加盐
            password = DigestUtil.md5Hex(password + SALT);
            user.setPassword(password);
        }
        boolean result = this.updateById(user);
        ExcUtils.throwIfTrue(!result, ExceptionCode.DATABASE_ERROR, "更新失败");
        // 更新 redis 缓存
        stringRedisTemplate.opsForValue().set(getUserIdKey(id), JSONUtil.toJsonStr(user), 1, TimeUnit.DAYS);
        return true;
    }

    @Override
    public Boolean isMe(Long id, HttpServletRequest request) {
        User user = loginUser.getLoginUser(request);
        return user.getId().equals(id);
    }
}
