package hk.ljx.fishpicsbackend.user.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.enums.Role;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.JwtUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.PasswordUtil;
import hk.ljx.fishpicsbackend.common.utils.PermissionUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.common.service.AuditLogWriter;
import hk.ljx.fishpicsbackend.common.utils.IpUtils;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.space.dto.CreateSpaceRequest;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.user.dto.UserEditByAdminRequest;
import hk.ljx.fishpicsbackend.user.dto.UserEditRequest;
import hk.ljx.fishpicsbackend.user.dto.UserLoginRequest;
import hk.ljx.fishpicsbackend.user.dto.UserQueryWrapper;
import hk.ljx.fishpicsbackend.user.dto.UserRegisterRequest;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import hk.ljx.fishpicsbackend.user.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.awt.Font;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final int USERNAME_MIN_LENGTH = 6;
    private static final int USERNAME_MAX_LENGTH = 30;
    private static final String DEFAULT_AVATAR_URL = "https://avatars.githubusercontent.com/u/179127403?v=4";

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceService spaceService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private RedisCacheManager cacheManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private AuditLogWriter auditLogWriter;

    // ==================== CAPTCHA ====================

    @Override
    public String getCheckCode(String str, Integer len, Integer minute) {
        int actualLen = len == null ? 4 : len;
        int actualMinute = minute == null ? 5 : minute;
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(200, 100, actualLen, 20);
        captcha.setFont(new Font("Monospaced", Font.BOLD, 80));
        stringRedisTemplate.opsForValue().set(str, captcha.getCode(), actualMinute, TimeUnit.MINUTES);
        return captcha.getImageBase64();
    }

    private void verifyRegisterCode(String captchaKey, String checkCode) {
        verifyAndConsume(RedisConstants.getRegisterCodeKey(captchaKey), checkCode);
    }

    private void verifyLoginCode(String captchaKey, String checkCode) {
        verifyAndConsume(RedisConstants.getLoginCodeKey(captchaKey), checkCode);
    }

    private void verifyAndConsume(String redisKey, String checkCode) {
        String cachedCode = stringRedisTemplate.opsForValue().get(redisKey);
        ExcUtils.throwIfTrue(cachedCode == null || !checkCode.equalsIgnoreCase(cachedCode),
                ExceptionCode.PARAMETER_ERROR, "验证码错误");
        stringRedisTemplate.delete(redisKey);
    }

    // ==================== USER CACHE ====================

    private void cacheLoginContext(User user) {
        List<SpaceTeamMember> teamMembers = Collections.emptyList();
        try {
            teamMembers = spaceTeamMemberMapper.selectList(
                    new LambdaQueryWrapper<SpaceTeamMember>()
                            .eq(SpaceTeamMember::getUserId, user.getId()));
        } catch (Exception e) {
            log.error("[UserServiceImpl] 加载团队成员失败, 用户权限将降级: userId={}", user.getId(), e);
        }
        LoginContext loginContext = PermissionUtils.buildLoginContext(user, teamMembers);
        cacheManager.getUserPermCache().put(String.valueOf(user.getId()), loginContext);
    }

    private void doRefreshUserInfoCache(User user) {
        User cacheUser = new User();
        BeanUtil.copyProperties(user, cacheUser, "password", "email", "phone");
        cacheManager.getUserInfoCache().evict(String.valueOf(user.getId()));
        cacheManager.getUserInfoCache().put(String.valueOf(user.getId()), cacheUser);
    }

    private void evictUserLoginContext(Long userId) {
        cacheManager.getUserPermCache().evict(String.valueOf(userId));
    }

    private void invalidateUserTokens(Long userId) {
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserTokenInvalidBeforeKey(userId),
                String.valueOf(System.currentTimeMillis()),
                RedisConstants.USER_TOKEN_INVALID_TTL_DAYS,
                TimeUnit.DAYS
        );
        evictUserLoginContext(userId);
    }

    // ==================== REGISTER & LOGIN ====================

    @Override
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

        verifyRegisterCode(captchaKey, checkCode);

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
            log.warn("注册用户名唯一索引冲突: username={}", username);
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "账号已存在");
        }

        Boolean spaceCreated = spaceService.createSpace(
                new CreateSpaceRequest(user.getNickname() + "的私人空间", "你的专属私密存储空间", 0),
                user
        );
        ExcUtils.throwIfTrue(!Boolean.TRUE.equals(spaceCreated), ExceptionCode.DATABASE_ERROR, "创建私人空间失败");
        return Response.ok(true);
    }

    @Override
    public Response<UserVO> userLogin(UserLoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        String checkCode = request.getCheckCode();
        String captchaKey = request.getCaptchaKey();
        ExcUtils.throwIfTrue(StrUtil.hasBlank(username, password, checkCode, captchaKey),
                ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        verifyLoginCode(captchaKey, checkCode);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        boolean credentialOk = PasswordUtil.matches(password,
                user != null ? user.getPassword() : null);
        if (user == null || !credentialOk) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "账号或密码错误");
        }
        ExcUtils.throwIfTrue(!user.isActive(),
                ExceptionCode.PARAMETER_ERROR, "账号已被禁用");

        String jwt = jwtUtils.sign(user.getId());
        refreshUserInfoCache(user);
        cacheLoginContext(user);

        try {
            SysAuditLog auditLog = new SysAuditLog();
            auditLog.setUserId(user.getId());
            auditLog.setUsername(user.getUsername());
            auditLog.setModule("用户管理");
            auditLog.setOperation("用户登录");
            auditLog.setResult(1);
            auditLog.setCreateTime(LocalDateTime.now());
            auditLogWriter.saveAsync(auditLog);
        } catch (Exception e) {
            log.warn("保存登录审计日志失败: userId={}", user.getId(), e);
        }

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

    // ==================== SELF-SERVICE ====================

    @Override
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
        refreshUserInfoCache(freshUser);
        evictUserLoginContext(id);

        if (passwordChanged) {
            invalidateUserTokens(id);
            if (StrUtil.isNotBlank(currentJwt)) {
                jwtUtils.addToBlacklist(currentJwt);
            }
        }
        return true;
    }

    // ==================== ADMIN ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean setStatus(Long userId) {
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "未找到该用户");
        if (Role.isAdmin(user.getRole())) {
            throw new BaseException(ExceptionCode.FORBIDDEN, "不能禁用管理员");
        }

        int newStatus = ExcUtils.eq(user.getStatus(), 1) ? 0 : 1;
        int affected = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getStatus, user.getStatus())
                .set(User::getStatus, newStatus));
        ExcUtils.throwIfTrue(affected == 0, ExceptionCode.CONFLICT, "操作冲突，请重试");

        user.setStatus(newStatus);
        Long finalUserId = userId;
        int finalNewStatus = newStatus;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    refreshUserInfoCache(user);
                    invalidateUserTokens(finalUserId);
                    if (finalNewStatus == 0) {
                        stringRedisTemplate.opsForSet().add(RedisConstants.BANNED_USERS_KEY, finalUserId.toString());
                    } else {
                        stringRedisTemplate.opsForSet().remove(RedisConstants.BANNED_USERS_KEY, finalUserId.toString());
                    }
                } catch (Exception e) {
                    log.error("[UserServiceImpl] setStatus 后缓存/令牌清理失败: userId={}, error={}",
                            finalUserId, e.getMessage(), e);
                }
            }
        });
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editUser(UserEditByAdminRequest request) {
        Long id = request.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "用户 ID 不能为空");

        User currentOperator = LoginContextHelper.requireUser();

        User user = userMapper.selectById(id);
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "未找到该用户");

        boolean passwordChanged = StrUtil.isNotBlank(request.getPassword());
        if (passwordChanged) {
            String pwd = request.getPassword();
            ExcUtils.throwIfTrue(pwd.length() < PASSWORD_MIN_LENGTH || pwd.length() > PASSWORD_MAX_LENGTH,
                    ExceptionCode.PARAMETER_ERROR, "密码长度必须为 8-32 位");
            request.setPassword(PasswordUtil.encode(pwd));
        }

        Long originalId = user.getId();
        Integer originalStatus = user.getStatus();
        Integer originalRole = user.getRole();
        Integer newRole = request.getRole();

        ensureAdminCanEdit(currentOperator, originalRole, newRole);
        protectLastAdmin(originalRole, newRole);
        validateAdminEditFields(request);
        cleanAdminEditRequest(request);

        BeanUtil.copyProperties(
                request,
                user,
                CopyOptions.create().setIgnoreNullValue(true).setIgnoreProperties("id", "status")
        );
        user.setId(originalId);
        user.setStatus(originalStatus);

        int rows;
        if (Role.isAdmin(originalRole) && newRole != null && !Role.isAdmin(newRole)) {
            rows = userMapper.updateRoleIfNotLastAdmin(originalId);
            ExcUtils.throwIfTrue(rows != 1, ExceptionCode.FORBIDDEN,
                    "系统至少需要保留一名管理员,无法降级最后一名 admin");
            user.setRole(Role.NORMAL.getCode());
            rows = userMapper.updateById(user);
        } else {
            rows = userMapper.updateById(user);
        }
        ExcUtils.throwIfTrue(rows != 1, ExceptionCode.DATABASE_ERROR, "更新用户失败");

        refreshUserInfoCache(user);
        evictUserLoginContext(id);
        if (passwordChanged) {
            invalidateUserTokens(id);
        }
        log.info("[UserServiceImpl] 管理员编辑用户清理: id={}, passwordChanged={}", id, passwordChanged);
        return true;
    }

    // ==================== QUERY ====================

    @Override
    public IPage<UserVO> getUserList(UserQueryWrapper wrapper, long current, long pageSize) {
        ExcUtils.throwIfTrue(current <= 0 || pageSize <= 0, ExceptionCode.PARAMETER_ERROR);
        QueryWrapper<User> qw = buildQueryWrapper(wrapper);
        IPage<User> userPage = userMapper.selectPage(new Page<>(current, pageSize), qw);
        return userPage.convert(this::toAdminVO);
    }

    @Override
    public Boolean isMe(Long id) {
        User user = LoginContextHelper.requireUser();
        return user.getId().equals(id);
    }

    @Override
    public UserVO getMyselfMessage() {
        User user = LoginContextHelper.requireUser();
        User fresh = userMapper.selectById(user.getId());
        ExcUtils.throwIfTrue(fresh == null, ExceptionCode.NOT_FOUND, "用户不存在");
        return UserVO.ofInfo(fresh.getId(), fresh.getUsername(), fresh.getNickname(), fresh.getAvatar(),
                fresh.getEmail(), fresh.getPhone(), fresh.getLevel(), fresh.getRole(), null,
                fresh.getCreateTime());
    }

    @Override
    public UserVO getUserProfile(Long userId) {
        User currentUser = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(userId == null, ExceptionCode.PARAMETER_ERROR);
        User targetUser = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(targetUser == null || targetUser.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");
        ExcUtils.throwIfTrue(!canViewProfile(currentUser.getId(), targetUser.getId()),
                ExceptionCode.FORBIDDEN, "无权查看该用户资料");
        return UserVO.ofPublicProfile(
                targetUser.getId(), targetUser.getUsername(), targetUser.getNickname(),
                targetUser.getAvatar(), targetUser.getLevel(), targetUser.getCreateTime());
    }

    @Override
    public List<UserVO> searchUsers(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return new ArrayList<>();
        }
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(w -> w.like(User::getUsername, escaped).or().like(User::getNickname, escaped));
        queryWrapper.eq(User::getStatus, 1).last("LIMIT 20");
        return userMapper.selectList(queryWrapper).stream()
                .map(user -> UserVO.ofSearch(user.getId(), user.getNickname(), user.getAvatar()))
                .collect(Collectors.toList());
    }

    @Override
    public void refreshUserInfoCache(User user) {
        doRefreshUserInfoCache(user);
    }

    @Override
    public UserVO adminGetUser(Long userId) {
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");
        return toAdminVO(user);
    }

    @Override
    public UserVO adminGetUserDetail(Long userId) {
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");
        return UserVO.ofAdmin(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(),
                user.getEmail(), user.getPhone(), user.getStatus(), user.getLevel(), user.getRole(),
                user.getCreateTime(), null, false);
    }

    @Override
    public UserVO getCurrentUserVO() {
        LoginContext ctx = LoginContextHelper.requireLoginContext();
        List<String> allPerms = new ArrayList<>(
                ctx.getSystemPerms() != null ? ctx.getSystemPerms() : List.of());
        if (ctx.getVipPerms() != null) {
            allPerms.addAll(ctx.getVipPerms());
        }
        return UserVO.builder()
                .id(ctx.getUserId()).username(ctx.getUsername()).nickname(ctx.getNickname())
                .avatar(ctx.getAvatar()).level(ctx.getLevel()).roleId(ctx.getRole())
                .permissions(allPerms).build();
    }

    // ==================== PRIVATE HELPERS ====================

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
            request.setNickname(XssSanitizer.cleanIfNotBlank(request.getNickname()));
        }
        if (StrUtil.isNotBlank(request.getUsername())) {
            request.setUsername(XssSanitizer.cleanIfNotBlank(request.getUsername()));
            validateUsername(request.getUsername(), "账号长度必须为 6-30 位");
            Long dupCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getUsername())
                    .ne(User::getId, request.getId()));
            ExcUtils.throwIfTrue(dupCount > 0, ExceptionCode.PARAMETER_ERROR, "用户名已被占用");
        }
    }

    private void validateUsername(String username, String message) {
        ExcUtils.throwIfTrue(username == null || username.length() < USERNAME_MIN_LENGTH
                        || username.length() > USERNAME_MAX_LENGTH,
                ExceptionCode.PARAMETER_ERROR, message);
    }

    private void validatePassword(String password) {
        ExcUtils.throwIfTrue(password == null || password.length() < PASSWORD_MIN_LENGTH
                        || password.length() > PASSWORD_MAX_LENGTH,
                ExceptionCode.PARAMETER_ERROR, "密码长度必须为 8-32 位");
    }

    private void ensureAdminCanEdit(User currentOperator, Integer originalRole, Integer newRole) {
        if (Role.isAdmin(originalRole) && !Role.isAdmin(currentOperator.getRole())) {
            throw new BaseException(ExceptionCode.FORBIDDEN, "只有管理员才能修改管理员信息");
        }
        if (Role.isAdmin(newRole)) {
            ExcUtils.throwIfTrue(!Role.isAdmin(currentOperator.getRole()),
                    ExceptionCode.FORBIDDEN, "只有管理员才能提升用户为管理员");
        }
    }

    private void protectLastAdmin(Integer originalRole, Integer newRole) {
        if (Role.isAdmin(originalRole) && newRole != null && !Role.isAdmin(newRole)) {
            Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, Role.ADMIN.getCode()).eq(User::getStatus, 1));
            ExcUtils.throwIfTrue(adminCount != null && adminCount <= 1,
                    ExceptionCode.FORBIDDEN, "系统至少需要保留一名管理员,无法降级最后一名 admin");
        }
    }

    private void validateAdminEditFields(UserEditByAdminRequest request) {
        if (request.getUsername() != null) {
            ExcUtils.throwIfTrue(request.getUsername().length() < USERNAME_MIN_LENGTH
                            || request.getUsername().length() > USERNAME_MAX_LENGTH,
                    ExceptionCode.PARAMETER_ERROR, "账号长度必须为 6-30 位");
            Long dupCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getUsername()).ne(User::getId, request.getId()));
            ExcUtils.throwIfTrue(dupCount > 0, ExceptionCode.PARAMETER_ERROR, "用户名已被占用");
        }
        if (request.getNickname() != null) {
            ExcUtils.throwIfTrue(request.getNickname().length() < NICKNAME_MIN_LENGTH
                            || request.getNickname().length() > NICKNAME_MAX_LENGTH,
                    ExceptionCode.PARAMETER_ERROR, "昵称长度必须为 1-30 位");
        }
        if (StrUtil.isNotBlank(request.getEmail())) {
            ExcUtils.throwIfTrue(!Validator.isEmail(request.getEmail()),
                    ExceptionCode.PARAMETER_ERROR, "邮箱格式不正确");
        }
        if (StrUtil.isNotBlank(request.getPhone())) {
            ExcUtils.throwIfTrue(!request.getPhone().matches("^1[3-9]\\d{9}$"),
                    ExceptionCode.PARAMETER_ERROR, "手机号格式不正确");
        }
        if (request.getLevel() != null) {
            ExcUtils.throwIfTrue(request.getLevel() < 0 || request.getLevel() > 2,
                    ExceptionCode.PARAMETER_ERROR, "用户等级必须为 0-2");
        }
        if (request.getRole() != null) {
            ExcUtils.throwIfTrue(request.getRole() < 0 || request.getRole() > 1,
                    ExceptionCode.PARAMETER_ERROR, "用户角色必须为 0-1");
        }
    }

    private void cleanAdminEditRequest(UserEditByAdminRequest request) {
        request.setUsername(XssSanitizer.cleanIfNotBlank(request.getUsername()));
        request.setNickname(XssSanitizer.cleanIfNotBlank(request.getNickname()));
        request.setEmail(XssSanitizer.cleanIfNotBlank(request.getEmail()));
        request.setPhone(XssSanitizer.cleanIfNotBlank(request.getPhone()));
    }

    private QueryWrapper<User> buildQueryWrapper(UserQueryWrapper userQueryWrapper) {
        Long id = userQueryWrapper.getId();
        String username = userQueryWrapper.getUsername();
        String email = userQueryWrapper.getEmail();
        String phone = userQueryWrapper.getPhone();
        String nickname = userQueryWrapper.getNickname();
        Integer status = userQueryWrapper.getStatus();
        LocalDateTime createTime = userQueryWrapper.getCreateTime();
        String sortField = userQueryWrapper.getSortField();
        String sortOrder = userQueryWrapper.getSortOrder();

        Set<String> allowedSortFields = Set.of(
                "id", "username", "email", "phone", "nickname", "status", "level", "create_time", "update_time");
        boolean validSortField = sortField != null && allowedSortFields.contains(sortField);

        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq(ObjectUtil.isNotNull(id), "id", id)
                .like(ObjectUtil.isNotNull(username), "username", username)
                .like(ObjectUtil.isNotNull(email), "email", email)
                .like(ObjectUtil.isNotNull(phone), "phone", phone)
                .like(ObjectUtil.isNotNull(nickname), "nickname", nickname)
                .eq(ObjectUtil.isNotNull(status), "status", status)
                .eq(ObjectUtil.isNotNull(createTime), "create_time", createTime);
        qw.orderBy(validSortField, "ascend".equals(sortOrder), sortField);
        return qw;
    }

    private boolean canViewProfile(Long currentUserId, Long targetUserId) {
        if (Objects.equals(currentUserId, targetUserId)) return true;
        LoginContext ctx = UserHolder.getLoginContext();
        if (ctx != null && ctx.hasSystemPerm("system:user:manage")) return true;
        return sharesTeam(currentUserId, targetUserId);
    }

    private boolean sharesTeam(Long currentUserId, Long targetUserId) {
        List<SpaceTeamMember> memberships = spaceTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .in(SpaceTeamMember::getUserId, List.of(currentUserId, targetUserId))
                        .select(SpaceTeamMember::getSpaceId, SpaceTeamMember::getUserId));
        Set<Long> currentSpaceIds = memberships.stream()
                .filter(m -> Objects.equals(m.getUserId(), currentUserId))
                .map(SpaceTeamMember::getSpaceId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return memberships.stream()
                .anyMatch(m -> Objects.equals(m.getUserId(), targetUserId)
                        && currentSpaceIds.contains(m.getSpaceId()));
    }

    private UserVO toAdminVO(User user) {
        return UserVO.ofAdmin(user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatar(), user.getEmail(), user.getPhone(), user.getStatus(),
                user.getLevel(), user.getRole(), user.getCreateTime(), null);
    }
}
