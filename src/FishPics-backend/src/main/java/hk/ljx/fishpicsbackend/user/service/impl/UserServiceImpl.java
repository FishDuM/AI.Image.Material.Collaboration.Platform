package hk.ljx.fishpicsbackend.user.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.JwtUtils;
import hk.ljx.fishpicsbackend.common.utils.PermissionUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.space.dto.CreateSpace;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.user.dto.UserEditByAdminRequest;
import hk.ljx.fishpicsbackend.user.dto.UserEditRequest;
import hk.ljx.fishpicsbackend.user.dto.UserLoginRequest;
import hk.ljx.fishpicsbackend.user.dto.UserQueryWrapper;
import hk.ljx.fishpicsbackend.user.dto.UserRequestRequest;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import hk.ljx.fishpicsbackend.user.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.DEFAULT_NICK_NAME;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.SALT;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpaceService spaceService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private MultiLevelCacheManager cacheManager;

    @Override
    public String getCheckCode(String str, Integer len, Integer minute) {
        int actualLen = len == null ? 4 : len;
        int actualMinute = minute == null ? 5 : minute;

        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(200, 100, actualLen, 20);
        captcha.setFont(new Font("Monospaced", Font.BOLD, 80));

        stringRedisTemplate.opsForValue().set(str, captcha.getCode(), actualMinute, TimeUnit.MINUTES);
        return captcha.getImageBase64();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> userRegister(UserRequestRequest userRequestRequest) {
        String username = userRequestRequest.getUsername();
        String password = userRequestRequest.getPassword();
        String checkPassword = userRequestRequest.getCheckPassword();
        String checkCode = userRequestRequest.getCheckCode();
        String captchaKey = userRequestRequest.getCaptchaKey();

        ExcUtils.throwIfTrue(StrUtil.hasBlank(checkCode, captchaKey), ExceptionCode.PARAMETER_ERROR, "验证码不能为空");
        ExcUtils.throwIfTrue(username == null || username.length() < 6 || username.length() > 11,
                ExceptionCode.PARAMETER_ERROR, "账号长度必须为 6-11 位");
        ExcUtils.throwIfTrue(password == null || password.length() < 8 || password.length() > 20,
                ExceptionCode.PARAMETER_ERROR, "密码长度必须为 8-20 位");
        ExcUtils.throwIfTrue(!StrUtil.equals(password, checkPassword), ExceptionCode.PARAMETER_ERROR, "两次密码不一致");

        String captchaRedisKey = RedisConstants.getRegisterCodeKey(captchaKey);
        String cachedCode = stringRedisTemplate.opsForValue().get(captchaRedisKey);
        ExcUtils.throwIfTrue(cachedCode == null || !checkCode.equalsIgnoreCase(cachedCode),
                ExceptionCode.PARAMETER_ERROR, "验证码错误");

        Long count = baseMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        ExcUtils.throwIfTrue(count != 0, ExceptionCode.PARAMETER_ERROR, "账号已存在");

        stringRedisTemplate.delete(captchaRedisKey);

        User user = new User();
        user.setUsername(username);
        user.setPassword(DigestUtil.md5Hex(password + SALT));
        user.setNickname(DEFAULT_NICK_NAME + RandomUtil.randomString(6));
        user.setAvatar("https://avatars.githubusercontent.com/u/179127403?v=4");

        int inserted = baseMapper.insert(user);
        ExcUtils.throwIfTrue(inserted != 1, ExceptionCode.DATABASE_ERROR, "注册失败");

        Boolean spaceCreated = spaceService.createSpace(
                new CreateSpace(user.getNickname() + "的私人空间", "你的专属私密存储空间", 0),
                user
        );
        ExcUtils.throwIfTrue(!Boolean.TRUE.equals(spaceCreated), ExceptionCode.DATABASE_ERROR, "创建私人空间失败");
        return ResUtils.success(true);
    }

    @Override
    public Response<UserVO> userLogin(UserLoginRequest userLoginRequest) {
        String username = userLoginRequest.getUsername();
        String password = userLoginRequest.getPassword();
        String checkCode = userLoginRequest.getCheckCode();
        String captchaKey = userLoginRequest.getCaptchaKey();
        ExcUtils.throwIfTrue(StrUtil.hasBlank(username, password, checkCode, captchaKey),
                ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        String loginCaptchaKey = RedisConstants.getLoginCodeKey(captchaKey);
        String cachedCode = stringRedisTemplate.opsForValue().get(loginCaptchaKey);
        ExcUtils.throwIfTrue(cachedCode == null || !cachedCode.equalsIgnoreCase(checkCode),
                ExceptionCode.PARAMETER_ERROR, "验证码错误");

        User user = baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getPassword, DigestUtil.md5Hex(password + SALT)));
        ExcUtils.throwIfTrue(user == null, ExceptionCode.PARAMETER_ERROR, "账号或密码错误");
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(user.getStatus()), ExceptionCode.PARAMETER_ERROR, "账号已被禁用");

        stringRedisTemplate.delete(loginCaptchaKey);

        String jwt = jwtUtils.sign(user.getId());
        refreshUserInfoCache(user);
        cacheLoginContext(user);

        return ResUtils.success(UserVO.ofLogin(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getLevel(),
                jwt,
                PermissionUtils.getPermissionsByLevel(user.getLevel())
        ));
    }

    private void cacheLoginContext(User user) {
        LoginContext loginContext = PermissionUtils.buildLoginContext(user);
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserPermCtxKey(user.getId()),
                JSONUtil.toJsonStr(loginContext),
                RedisConstants.USER_PERM_CTX_TTL,
                TimeUnit.DAYS
        );
    }

    private void refreshUserInfoCache(User user) {
        User cacheUser = new User();
        BeanUtil.copyProperties(user, cacheUser, "password", "email", "phone");
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserInfoKey(user.getId()),
                JSONUtil.toJsonStr(cacheUser),
                RedisConstants.USER_PERM_CTX_TTL,
                TimeUnit.DAYS
        );
        cacheManager.getUserInfoCache().evict(String.valueOf(user.getId()));
    }

    private void evictUserLoginContext(Long userId) {
        stringRedisTemplate.delete(RedisConstants.getUserPermCtxKey(userId));
        cacheManager.getUserPermCache().evict(String.valueOf(userId));
    }

    private void invalidateUserTokens(Long userId) {
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserTokenInvalidBeforeKey(userId),
                String.valueOf(System.currentTimeMillis()),
                RedisConstants.USER_PERM_CTX_TTL,
                TimeUnit.DAYS
        );
        evictUserLoginContext(userId);
    }

    private QueryWrapper<User> newQueryWrapper(UserQueryWrapper userQueryWrapper) {
        Long id = userQueryWrapper.getId();
        String username = userQueryWrapper.getUsername();
        String email = userQueryWrapper.getEmail();
        String phone = userQueryWrapper.getPhone();
        String nickname = userQueryWrapper.getNickname();
        Integer status = userQueryWrapper.getStatus();
        Date createTime = userQueryWrapper.getCreateTime();
        String sortField = userQueryWrapper.getSortField();
        String sortOrder = userQueryWrapper.getSortOrder();

        Set<String> allowedSortFields = Set.of(
                "id", "username", "email", "phone", "nickname", "status", "level", "create_time", "update_time"
        );
        boolean validSortField = sortField != null && allowedSortFields.contains(sortField);

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (ObjectUtil.isNotNull(id)) {
            queryWrapper.eq("id", id);
        }
        if (ObjectUtil.isNotNull(username)) {
            queryWrapper.like("username", username);
        }
        if (ObjectUtil.isNotNull(email)) {
            queryWrapper.like("email", email);
        }
        if (ObjectUtil.isNotNull(phone)) {
            queryWrapper.like("phone", phone);
        }
        if (ObjectUtil.isNotNull(nickname)) {
            queryWrapper.like("nickname", nickname);
        }
        if (ObjectUtil.isNotNull(status)) {
            queryWrapper.eq("status", status);
        }
        if (ObjectUtil.isNotNull(createTime)) {
            queryWrapper.eq("create_time", createTime);
        }
        queryWrapper.orderBy(validSortField, "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public IPage<UserVO> getUserList(UserQueryWrapper userQueryWrapper, long current, long pageSize) {
        ExcUtils.throwIfTrue(current <= 0 || pageSize <= 0, ExceptionCode.PARAMETER_ERROR);
        IPage<User> userPage = baseMapper.selectPage(new Page<>(current, pageSize), newQueryWrapper(userQueryWrapper));
        return userPage.convert(user -> UserVO.ofAdmin(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getLevel(),
                user.getCreateTime(),
                null
        ));
    }

    @Override
    public Boolean setStatus(Long userId) {
        User user = baseMapper.selectById(userId);
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "未找到该用户");
        if (isAdmin(user)) {
            throw new BaseException(ExceptionCode.FORBIDDEN, "不能禁用管理员");
        }

        int newStatus = Integer.valueOf(1).equals(user.getStatus()) ? 0 : 1;
        int affected = baseMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getStatus, user.getStatus())
                .set(User::getStatus, newStatus));
        ExcUtils.throwIfTrue(affected == 0, ExceptionCode.CONFLICT, "操作冲突，请重试");

        user.setStatus(newStatus);
        refreshUserInfoCache(user);
        invalidateUserTokens(userId);

        if (newStatus == 0) {
            stringRedisTemplate.opsForSet().add(RedisConstants.BANNED_USERS_KEY, userId.toString());
        } else {
            stringRedisTemplate.opsForSet().remove(RedisConstants.BANNED_USERS_KEY, userId.toString());
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editUser(UserEditByAdminRequest userEditByAdminRequest) {
        Long id = userEditByAdminRequest.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "用户 ID 不能为空");

        User user = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "未找到该用户");

        boolean passwordChanged = StrUtil.isNotBlank(userEditByAdminRequest.getPassword());
        if (passwordChanged) {
            userEditByAdminRequest.setPassword(DigestUtil.md5Hex(userEditByAdminRequest.getPassword() + SALT));
        }

        Long originalId = user.getId();
        Integer originalStatus = user.getStatus();
        // 保留修改前的 level，用于管理员保护检查（检查原始身份，而非修改后的值）
        Integer originalLevel = user.getLevel();
        BeanUtil.copyProperties(
                userEditByAdminRequest,
                user,
                CopyOptions.create().setIgnoreNullValue(true).setIgnoreProperties("id", "status")
        );
        user.setId(originalId);
        user.setStatus(originalStatus);

        // 使用修改前的 level 判断目标是否为管理员，防止降级管理员时绕过保护
        boolean targetIsAdmin = originalLevel != null && originalLevel >= 3;
        if (targetIsAdmin) {
            LoginContext currentCtx = UserHolder.getLoginContext();
            ExcUtils.throwIfTrue(currentCtx == null, ExceptionCode.NOT_LOGIN);
            if (!currentCtx.isAdmin()) {
                throw new BaseException(ExceptionCode.FORBIDDEN, "只有管理员才能修改管理员信息");
            }
        }

        int rows = baseMapper.updateById(user);
        ExcUtils.throwIfTrue(rows != 1, ExceptionCode.DATABASE_ERROR, "更新用户失败");

        User freshUser = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(freshUser == null, ExceptionCode.NOT_FOUND, "未找到该用户");
        refreshUserInfoCache(freshUser);
        if (passwordChanged) {
            invalidateUserTokens(id);
        } else {
            evictUserLoginContext(id);
        }
        return true;
    }

    @Override
    public UserVO getMyselfMessage() {
        User currentUser = UserHolder.getUser();
        ExcUtils.throwIfTrue(currentUser == null, ExceptionCode.NOT_LOGIN);

        User user = baseMapper.selectById(currentUser.getId());
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "用户不存在");

        return UserVO.ofInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getEmail(),
                user.getPhone(),
                user.getLevel(),
                null,
                user.getCreateTime(),
                null,
                null,
                null,
                null
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editMyself(UserEditRequest userEditRequest) {
        Long id = userEditRequest.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR);

        // 授权检查前置：先确认是本人操作，再执行任何业务查询
        ExcUtils.throwIfTrue(!isMe(id), ExceptionCode.UNAUTHORIZED, "只可修改自己的信息");

        String nickname = userEditRequest.getNickname();
        if (nickname != null) {
            ExcUtils.throwIfTrue(nickname.length() < 6 || nickname.length() > 11,
                    ExceptionCode.PARAMETER_ERROR, "昵称长度必须为 6-11 位");
        }

        String username = userEditRequest.getUsername();
        if (username != null) {
            ExcUtils.throwIfTrue(username.length() <= 5 || username.length() >= 12,
                    ExceptionCode.PARAMETER_ERROR, "账号长度必须为 6-11 位");
            Long dupCount = baseMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, username)
                    .ne(User::getId, id));
            ExcUtils.throwIfTrue(dupCount > 0, ExceptionCode.PARAMETER_ERROR, "用户名已被占用");
        }

        String password = userEditRequest.getPassword();
        if (password != null) {
            ExcUtils.throwIfTrue(password.length() <= 7 || password.length() >= 21,
                    ExceptionCode.PARAMETER_ERROR, "密码长度必须为 8-20 位");
        }

        User user = getById(id);
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "用户不存在");

        String oldHashedPassword = user.getPassword();
        BeanUtil.copyProperties(
                userEditRequest,
                user,
                CopyOptions.create().ignoreNullValue().setIgnoreProperties("id", "password")
        );

        boolean passwordChanged = StrUtil.isNotBlank(password);
        if (passwordChanged) {
            String originalPassword = userEditRequest.getOriginalPassword();
            ExcUtils.throwIfTrue(StrUtil.isBlank(originalPassword), ExceptionCode.PARAMETER_ERROR, "请输入原始密码");
            ExcUtils.throwIfTrue(!DigestUtil.md5Hex(originalPassword + SALT).equals(oldHashedPassword),
                    ExceptionCode.PARAMETER_ERROR, "原始密码错误");
            user.setPassword(DigestUtil.md5Hex(password + SALT));
        }

        boolean updated = updateById(user);
        ExcUtils.throwIfTrue(!updated, ExceptionCode.DATABASE_ERROR, "更新失败");

        User freshUser = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(freshUser == null, ExceptionCode.NOT_FOUND, "用户不存在");
        refreshUserInfoCache(freshUser);
        if (passwordChanged) {
            invalidateUserTokens(id);
        } else {
            evictUserLoginContext(id);
        }
        return true;
    }

    @Override
    public Boolean isMe(Long id) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        return user.getId().equals(id);
    }

    private boolean isAdmin(User user) {
        return user.getLevel() != null && user.getLevel() >= 3;
    }

    @Override
    public UserVO getUserProfile(Long userId) {
        User currentUser = UserHolder.getUser();
        ExcUtils.throwIfTrue(currentUser == null, ExceptionCode.NOT_LOGIN);

        User targetUser = baseMapper.selectById(userId);
        ExcUtils.throwIfTrue(targetUser == null || targetUser.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");

        return UserVO.ofPublicProfile(
                targetUser.getId(),
                targetUser.getUsername(),
                targetUser.getNickname(),
                targetUser.getAvatar(),
                targetUser.getLevel(),
                targetUser.getCreateTime()
        );
    }

    @Override
    public List<UserVO> searchUsers(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        queryWrapper.eq(User::getStatus, 1);
        queryWrapper.last("LIMIT 20");
        return baseMapper.selectList(queryWrapper).stream()
                .map(user -> UserVO.ofSearch(user.getId(), user.getNickname(), user.getAvatar()))
                .collect(Collectors.toList());
    }
}
