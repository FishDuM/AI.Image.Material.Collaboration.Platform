package hk.ljx.fishpicsbackend.user.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.JwtUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.PasswordUtil;
import hk.ljx.fishpicsbackend.common.utils.PermissionUtils;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.space.dto.CreateSpaceRequest;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.user.dto.UserEditRequest;
import hk.ljx.fishpicsbackend.user.dto.UserLoginRequest;
import hk.ljx.fishpicsbackend.user.dto.UserRegisterRequest;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.DEFAULT_NICK_NAME;

@Component
@Slf4j
public class UserManager {

    private static final int USERNAME_MIN_LENGTH = 6;
    private static final int USERNAME_MAX_LENGTH = 30;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 32;
    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/179127403?v=4";

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceService spaceService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private CaptchaManager captchaManager;

    @Resource
    private UserCacheManager userCacheManager;

    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> userRegister(UserRegisterRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        String checkPassword = request.getCheckPassword();
        String checkCode = request.getCheckCode();
        String captchaKey = request.getCaptchaKey();

        ExcUtils.throwIfTrue(StrUtil.hasBlank(checkCode, captchaKey), ExceptionCode.PARAMETER_ERROR, "验证码不能为空");
        validateUsername(username, "账号长度必须为 6-30 位");
        validatePassword(password);
        ExcUtils.throwIfTrue(!StrUtil.equals(password, checkPassword), ExceptionCode.PARAMETER_ERROR, "两次密码不一致");

        captchaManager.verifyRegisterCode(captchaKey, checkCode);

        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        ExcUtils.throwIfTrue(count != 0, ExceptionCode.PARAMETER_ERROR, "账号已存在");

        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(password));
        user.setNickname(XssSanitizer.clean(DEFAULT_NICK_NAME + RandomUtil.randomString(6)));
        user.setAvatar(DEFAULT_AVATAR_URL);

        try {
            ExcUtils.throwIfTrue(userMapper.insert(user) != 1, ExceptionCode.DATABASE_ERROR, "注册失败");
        } catch (DataIntegrityViolationException e) {
            log.warn("register username unique index collision: username={}", username);
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "账号已存在");
        }

        Boolean spaceCreated = spaceService.createSpace(
                new CreateSpaceRequest(user.getNickname() + "的私人空间", "你的专属私密存储空间", 0),
                user
        );
        ExcUtils.throwIfTrue(!Boolean.TRUE.equals(spaceCreated), ExceptionCode.DATABASE_ERROR, "创建私人空间失败");
        return Response.ok(true);
    }

    public Response<UserVO> userLogin(UserLoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        String checkCode = request.getCheckCode();
        String captchaKey = request.getCaptchaKey();
        ExcUtils.throwIfTrue(StrUtil.hasBlank(username, password, checkCode, captchaKey),
                ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        captchaManager.verifyLoginCode(captchaKey, checkCode);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        boolean credentialOk = PasswordUtil.matchesWithDummyOnInvalidHash(
                password,
                user != null ? user.getPassword() : null
        );
        if (user == null || !credentialOk) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "账号或密码错误");
        }
        ExcUtils.throwIfTrue(!ExcUtils.eq(user.getStatus(), 1),
                ExceptionCode.PARAMETER_ERROR, "账号已被禁用");

        String jwt = jwtUtils.sign(user.getId());
        userCacheManager.refreshUserInfoCache(user);
        userCacheManager.cacheLoginContext(user);

        return Response.ok(UserVO.ofLogin(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getLevel(),
                user.getRole(),
                jwt,
                PermissionUtils.getPermissionsByLevel(user.getLevel(), user.getRole())
        ));
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean editMyself(UserEditRequest request, String currentJwt) {
        Long id = request.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR);
        User currentUser = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(!currentUser.getId().equals(id), ExceptionCode.FORBIDDEN, "只可修改自己的信息");

        validateOptionalProfileFields(request);

        User user = userMapper.selectById(id);
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "用户不存在");

        String oldHashedPassword = user.getPassword();
        BeanUtil.copyProperties(
                request,
                user,
                CopyOptions.create().ignoreNullValue().setIgnoreProperties("id", "password")
        );

        boolean passwordChanged = StrUtil.isNotBlank(request.getPassword());
        if (passwordChanged) {
            String originalPassword = request.getOriginalPassword();
            ExcUtils.throwIfTrue(StrUtil.isBlank(originalPassword), ExceptionCode.PARAMETER_ERROR, "请输入原始密码");
            ExcUtils.throwIfTrue(!PasswordUtil.matches(originalPassword, oldHashedPassword),
                    ExceptionCode.PARAMETER_ERROR, "原始密码错误");
            validatePassword(request.getPassword());
            user.setPassword(PasswordUtil.encode(request.getPassword()));
        }

        ExcUtils.throwIfTrue(userMapper.updateById(user) != 1, ExceptionCode.DATABASE_ERROR, "更新失败");

        User freshUser = userMapper.selectById(id);
        ExcUtils.throwIfTrue(freshUser == null, ExceptionCode.NOT_FOUND, "用户不存在");
        userCacheManager.refreshUserInfoCache(freshUser);
        userCacheManager.evictUserLoginContext(id);

        if (passwordChanged) {
            userCacheManager.invalidateUserTokens(id);
            if (StrUtil.isNotBlank(currentJwt)) {
                jwtUtils.addToBlacklist(currentJwt);
            }
        }
        return true;
    }

    private void validateOptionalProfileFields(UserEditRequest request) {
        if (request.getNickname() != null) {
            ExcUtils.throwIfTrue(request.getNickname().length() < USERNAME_MIN_LENGTH
                            || request.getNickname().length() > USERNAME_MAX_LENGTH,
                    ExceptionCode.PARAMETER_ERROR, "昵称长度必须为 6-30 位");
        }
        if (request.getUsername() != null) {
            ExcUtils.throwIfTrue(request.getUsername().length() < USERNAME_MIN_LENGTH
                            || request.getUsername().length() > USERNAME_MAX_LENGTH,
                    ExceptionCode.PARAMETER_ERROR, "账号长度必须为 6-30 位");
        }
        if (request.getPassword() != null) {
            validatePassword(request.getPassword());
        }

        if (StrUtil.isNotBlank(request.getNickname())) {
            request.setNickname(cleanPlain(request.getNickname()));
        }
        if (StrUtil.isNotBlank(request.getUsername())) {
            request.setUsername(cleanPlain(request.getUsername()));
            validateUsername(request.getUsername(), "账号长度必须为 6-30 位");
            Long dupCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getUsername())
                    .ne(User::getId, request.getId()));
            ExcUtils.throwIfTrue(dupCount > 0, ExceptionCode.PARAMETER_ERROR, "用户名已被占用");
        }
    }

    private void validateUsername(String username, String message) {
        ExcUtils.throwIfTrue(username == null
                        || username.length() < USERNAME_MIN_LENGTH
                        || username.length() > USERNAME_MAX_LENGTH,
                ExceptionCode.PARAMETER_ERROR, message);
    }

    private void validatePassword(String password) {
        ExcUtils.throwIfTrue(password == null
                        || password.length() < PASSWORD_MIN_LENGTH
                        || password.length() > PASSWORD_MAX_LENGTH,
                ExceptionCode.PARAMETER_ERROR, "密码长度必须为 8-32 位");
    }

    private String cleanPlain(String value) {
        return StrUtil.isBlank(value) ? value : XssSanitizer.clean(value);
    }
}
