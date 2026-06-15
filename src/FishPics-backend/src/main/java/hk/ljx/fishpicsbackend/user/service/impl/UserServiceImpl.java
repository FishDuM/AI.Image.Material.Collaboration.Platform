package hk.ljx.fishpicsbackend.user.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
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
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.JwtUtils;
import hk.ljx.fishpicsbackend.common.utils.PasswordUtil;
import hk.ljx.fishpicsbackend.common.utils.PermissionUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.DEFAULT_NICK_NAME;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private hk.ljx.fishpicsbackend.common.utils.RedisAtomicOps redisAtomicOps;

    @Resource
    private SpaceService spaceService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private MultiLevelCacheManager cacheManager;

    @Resource
    private hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper spaceTeamMemberMapper;

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
        // 用 getAndDelete 原子消费验证码（防并发复用）
        String cachedCode = redisAtomicOps.getAndDelete(captchaRedisKey);
        ExcUtils.throwIfTrue(cachedCode == null || !checkCode.equalsIgnoreCase(cachedCode),
                ExceptionCode.PARAMETER_ERROR, "验证码错误");

        Long count = baseMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        ExcUtils.throwIfTrue(count != 0, ExceptionCode.PARAMETER_ERROR, "账号已存在");

        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(password));
        // nickname 入库前清 HTML 标签
        user.setNickname(XssSanitizer.clean(DEFAULT_NICK_NAME + RandomUtil.randomString(6)));
        user.setAvatar("https://avatars.githubusercontent.com/u/179127403?v=4");

        int inserted;
        try {
            inserted = baseMapper.insert(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发注册 TOCTOU，unique 索引兜底抛异常
            log.warn("注册撞 username 唯一索引(并发): username={}", username);
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "账号已存在");
        }
        ExcUtils.throwIfTrue(inserted != 1, ExceptionCode.DATABASE_ERROR, "注册失败");

        Boolean spaceCreated = spaceService.createSpace(
                new CreateSpace(user.getNickname() + "的私人空间", "你的专属私密存储空间", 0),
                user
        );
        ExcUtils.throwIfTrue(!Boolean.TRUE.equals(spaceCreated), ExceptionCode.DATABASE_ERROR, "创建私人空间失败");
        return Response.ok(true);
    }

    @Override
    public Response<UserVO> userLogin(UserLoginRequest userLoginRequest) {
        String username = userLoginRequest.getUsername();
        String password = userLoginRequest.getPassword();
        String checkCode = userLoginRequest.getCheckCode();
        String captchaKey = userLoginRequest.getCaptchaKey();
        ExcUtils.throwIfTrue(StrUtil.hasBlank(username, password, checkCode, captchaKey),
                ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        // 验证码
        String loginCaptchaKey = RedisConstants.getLoginCodeKey(captchaKey);
        String cachedCode = redisAtomicOps.getAndDelete(loginCaptchaKey);
        ExcUtils.throwIfTrue(cachedCode == null || !checkCode.equalsIgnoreCase(cachedCode),
                ExceptionCode.PARAMETER_ERROR, "验证码错误");

        // 先按 username 查 user，再用 BCrypt 校验 — 防止时序攻击
        User user = baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        boolean credentialOk = user != null && PasswordUtil.matches(password, user.getPassword());

        if (user == null || !credentialOk) {
            // 统一错误消息，防用户名枚举
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "账号或密码错误");
        }
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(user.getStatus()),
                ExceptionCode.PARAMETER_ERROR, "账号已被禁用");

        // 登录成功
        String jwt = jwtUtils.sign(user.getId());
        refreshUserInfoCache(user);
        cacheLoginContext(user);

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

    private void cacheLoginContext(User user) {
        // 同时加载该用户的团队成员关系，注入 LoginContext.teams
        java.util.List<hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember> teamMembers = java.util.Collections.emptyList();
        try {
            teamMembers = spaceTeamMemberMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember>()
                            .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getUserId, user.getId()));
        } catch (Exception e) {
            log.warn("[UserService] 加载用户团队成员关系失败: userId={}", user.getId(), e);
        }
        LoginContext loginContext = PermissionUtils.buildLoginContext(user, teamMembers);
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserPermCtxKey(user.getId()),
                JSONUtil.toJsonStr(loginContext),
                RedisConstants.USER_PERM_CTX_TTL,
                TimeUnit.DAYS
        );
    }

    @Override
    public void refreshUserInfoCache(User user) {
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
                user.getRole(),
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
        // Redis 缓存清理包 try-catch，Redis 故障不让封禁失败
        // BANNED_USERS_KEY 的 add/remove 也包进同一个 try-catch
        try {
            refreshUserInfoCache(user);
            invalidateUserTokens(userId);
            if (newStatus == 0) {
                stringRedisTemplate.opsForSet().add(RedisConstants.BANNED_USERS_KEY, userId.toString());
            } else {
                stringRedisTemplate.opsForSet().remove(RedisConstants.BANNED_USERS_KEY, userId.toString());
            }
        } catch (Exception e) {
            log.error("[UserService] setStatus 缓存/Token/封禁集合清理失败(Redis 故障,以 DB 为准): userId={}, error={}",
                    userId, e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editUser(UserEditByAdminRequest userEditByAdminRequest) {
        Long id = userEditByAdminRequest.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "用户 ID 不能为空");

        User currentOperator = UserHolder.getUser();
        ExcUtils.throwIfTrue(currentOperator == null, ExceptionCode.NOT_LOGIN);
        // 不允许 admin 改自己（防止把最后一名 admin 降级或改密码踢自己）
        ExcUtils.throwIfTrue(Objects.equals(currentOperator.getId(), id),
                ExceptionCode.FORBIDDEN, "管理员不能通过此接口修改自己,请使用 /user/editUser");

        User user = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "未找到该用户");

        boolean passwordChanged = StrUtil.isNotBlank(userEditByAdminRequest.getPassword());
        // 密码改用 BCrypt
        if (passwordChanged) {
            // 同时校验密码强度
            String pwd = userEditByAdminRequest.getPassword();
            ExcUtils.throwIfTrue(pwd.length() < 8 || pwd.length() > 32,
                    ExceptionCode.PARAMETER_ERROR, "密码长度必须为 8-32 位");
            userEditByAdminRequest.setPassword(PasswordUtil.encode(pwd));
        }

        Long originalId = user.getId();
        Integer originalStatus = user.getStatus();
        // 保留修改前的 role,用于管理员保护检查(检查原始身份,而不是修改后的值)
        Integer originalRole = user.getRole();
        Integer newRole = userEditByAdminRequest.getRole();

        // 不允许非 admin 改 admin
        if (originalRole != null && originalRole == 1) {
            Integer currentOpRole = currentOperator.getRole();
            if (currentOpRole == null || currentOpRole != 1) {
                throw new BaseException(ExceptionCode.FORBIDDEN, "只有管理员才能修改管理员信息");
            }
        }

        // 非 admin 不能把别人提升为 admin
        if (newRole != null && newRole == 1) {
            Integer currentOpRole = currentOperator.getRole();
            ExcUtils.throwIfTrue(currentOpRole == null || currentOpRole != 1,
                    ExceptionCode.FORBIDDEN, "只有管理员才能提升用户为管理员");
        }

        // 最后一名 admin 保护：如果改完会让系统无 admin，拒绝
        if ((originalRole != null && originalRole == 1)
                && (newRole != null && newRole == 0)) {
            Long adminCount = baseMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getRole, 1)
                    .eq(User::getStatus, 1));
            ExcUtils.throwIfTrue(adminCount != null && adminCount <= 1,
                    ExceptionCode.FORBIDDEN, "系统至少需要保留一名管理员,无法降级最后一名 admin");
        }

        // 管理员编辑接口也需要 XSS 清洗
        if (StrUtil.isNotBlank(userEditByAdminRequest.getUsername())) {
            userEditByAdminRequest.setUsername(XssSanitizer.clean(userEditByAdminRequest.getUsername()));
        }
        if (StrUtil.isNotBlank(userEditByAdminRequest.getNickname())) {
            userEditByAdminRequest.setNickname(XssSanitizer.clean(userEditByAdminRequest.getNickname()));
        }
        if (StrUtil.isNotBlank(userEditByAdminRequest.getEmail())) {
            userEditByAdminRequest.setEmail(XssSanitizer.clean(userEditByAdminRequest.getEmail()));
        }
        if (StrUtil.isNotBlank(userEditByAdminRequest.getPhone())) {
            userEditByAdminRequest.setPhone(XssSanitizer.clean(userEditByAdminRequest.getPhone()));
        }

        BeanUtil.copyProperties(
                userEditByAdminRequest,
                user,
                CopyOptions.create().setIgnoreNullValue(true).setIgnoreProperties("id", "status")
        );
        user.setId(originalId);
        user.setStatus(originalStatus);

        // 如果是"降级 admin"路径，用 updateRoleIfNotLastAdmin 条件 UPDATE
        int rows;
        if ((originalRole != null && originalRole == 1)
                && (newRole != null && newRole == 0)) {
            int roleRows = baseMapper.updateRoleIfNotLastAdmin(originalId);
            ExcUtils.throwIfTrue(roleRows != 1, ExceptionCode.FORBIDDEN,
                    "系统至少需要保留一名管理员,无法降级最后一名 admin");
            // role 已用条件 UPDATE 改完,user 内存对象同步
            user.setRole(0);
            // 其他字段照常 update
            rows = baseMapper.updateById(user);
            ExcUtils.throwIfTrue(rows != 1, ExceptionCode.DATABASE_ERROR, "更新用户失败");
        } else {
            rows = baseMapper.updateById(user);
            ExcUtils.throwIfTrue(rows != 1, ExceptionCode.DATABASE_ERROR, "更新用户失败");
        }

        // 管理员编辑后必须清缓存/失效 Token，否则 LoginContext 缓存可能失效
        try {
            refreshUserInfoCache(user);
            evictUserLoginContext(id);
            invalidateUserTokens(id);
            log.info("[UserService] admin editUser 后缓存/Token 清理: id={}, passwordChanged={}", id, passwordChanged);
        } catch (Exception e) {
            // 缓存清理失败不影响主流程(DB 已提交),只 log
            log.warn("[UserService] admin editUser 缓存清理失败(需关注): id={}", id, e);
        }
        return true;
    }

    /**
     * 修改自己的信息(用户改自己)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean editMyself(UserEditRequest userEditRequest, String currentJwt) {
        Long id = userEditRequest.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR);

        // UNAUTHORIZED(401) 表示"未认证"，此场景是"已认证但不能改别人" → FORBIDDEN(403)
        ExcUtils.throwIfTrue(!isMe(id), ExceptionCode.FORBIDDEN, "只可修改自己的信息");

        String nickname = userEditRequest.getNickname();
        if (nickname != null) {
            ExcUtils.throwIfTrue(nickname.length() < 6 || nickname.length() > 11,
                    ExceptionCode.PARAMETER_ERROR, "昵称长度必须为 6-11 位");
        }

        String username = userEditRequest.getUsername();
        if (username != null) {
            ExcUtils.throwIfTrue(username.length() <= 5 || username.length() >= 12,
                    ExceptionCode.PARAMETER_ERROR, "账号长度必须为 6-11 位");
        }

        String password = userEditRequest.getPassword();
        if (password != null) {
            ExcUtils.throwIfTrue(password.length() <= 7 || password.length() >= 21,
                    ExceptionCode.PARAMETER_ERROR, "密码长度必须为 8-20 位");
        }

        User user = getById(id);
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_FOUND, "用户不存在");

        // XSS 清洗 TOCTOU：先清洗再用清洗后的值查重 + 入库
        if (StrUtil.isNotBlank(userEditRequest.getNickname())) {
            userEditRequest.setNickname(XssSanitizer.clean(userEditRequest.getNickname()));
            // nickname 清洗后（可能清空）再校验长度
            ExcUtils.throwIfTrue(userEditRequest.getNickname().length() < 6 || userEditRequest.getNickname().length() > 11,
                    ExceptionCode.PARAMETER_ERROR, "昵称长度必须为 6-11 位");
        }
        if (StrUtil.isNotBlank(userEditRequest.getUsername())) {
            userEditRequest.setUsername(XssSanitizer.clean(userEditRequest.getUsername()));
        }
        // 清洗后长度可能变化(被清空),重新校验
        if (StrUtil.isNotBlank(userEditRequest.getUsername())) {
            ExcUtils.throwIfTrue(userEditRequest.getUsername().length() <= 5 || userEditRequest.getUsername().length() >= 12,
                    ExceptionCode.PARAMETER_ERROR, "账号长度必须为 6-11 位");
            Long dupCount = baseMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, userEditRequest.getUsername())
                    .ne(User::getId, id));
            ExcUtils.throwIfTrue(dupCount > 0, ExceptionCode.PARAMETER_ERROR, "用户名已被占用");
        }

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
            ExcUtils.throwIfTrue(!PasswordUtil.matches(originalPassword, oldHashedPassword),
                    ExceptionCode.PARAMETER_ERROR, "原始密码错误");
            ExcUtils.throwIfTrue(password.length() < 8 || password.length() > 32,
                    ExceptionCode.PARAMETER_ERROR, "密码长度必须为 8-32 位");
            user.setPassword(PasswordUtil.encode(password));
        }

        boolean updated = updateById(user);
        ExcUtils.throwIfTrue(!updated, ExceptionCode.DATABASE_ERROR, "更新失败");

        User freshUser = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(freshUser == null, ExceptionCode.NOT_FOUND, "用户不存在");
        refreshUserInfoCache(freshUser);
        // editMyself 改 nickname/username 后，LoginContext 缓存需更新
        evictUserLoginContext(id);

        if (passwordChanged) {
            invalidateUserTokens(id);
            if (StrUtil.isNotBlank(currentJwt)) {
                jwtUtils.addToBlacklist(currentJwt);
            }
        }
        return true;
    }

    @Override
    public Boolean isMe(Long id) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        return user.getId().equals(id);
    }

    @Override
    public UserVO getMyselfMessage() {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        User fresh = baseMapper.selectById(user.getId());
        ExcUtils.throwIfTrue(fresh == null, ExceptionCode.NOT_FOUND, "用户不存在");
        return UserVO.ofInfo(fresh.getId(), fresh.getUsername(), fresh.getNickname(), fresh.getAvatar(),
                fresh.getEmail(), fresh.getPhone(), fresh.getLevel(), fresh.getRole(), null,
                fresh.getCreateTime(), null, null, null, null);
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && user.getRole() == 1;
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
        // 转义 LIKE 通配符（%、_、\）
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper.like(User::getUsername, escaped).or().like(User::getNickname, escaped));
        queryWrapper.eq(User::getStatus, 1);
        queryWrapper.last("LIMIT 20");
        return baseMapper.selectList(queryWrapper).stream()
                .map(user -> UserVO.ofSearch(user.getId(), user.getNickname(), user.getAvatar()))
                .collect(Collectors.toList());
    }
}
