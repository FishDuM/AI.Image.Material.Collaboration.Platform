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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.JwtUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.SysUserRoleMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.permission.entity.SysUserRole;
import hk.ljx.fishpicsbackend.permission.service.PermissionService;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.space.dto.CreateSpace;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.dto.UserEditByAdminRequest;
import hk.ljx.fishpicsbackend.user.dto.UserEditRequest;
import hk.ljx.fishpicsbackend.user.dto.UserLoginRequest;
import hk.ljx.fishpicsbackend.user.dto.UserPrivacyRequest;
import hk.ljx.fishpicsbackend.user.dto.UserQueryWrapper;
import hk.ljx.fishpicsbackend.user.dto.UserRequestRequest;
import hk.ljx.fishpicsbackend.user.service.UserService;
import hk.ljx.fishpicsbackend.user.vo.AdminGetUserVO;
import hk.ljx.fishpicsbackend.user.vo.UserLoginVO;
import hk.ljx.fishpicsbackend.user.vo.UserMessageVO;
import hk.ljx.fishpicsbackend.user.vo.UserPublicProfileVO;
import hk.ljx.fishpicsbackend.user.vo.UserSearchVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private MultiLevelCacheManager cacheManager;

    @Override
    public String getCheckCode(String str, Integer len, Integer minute) {

        // 如果没有传验证码长度或过期时间则用默认值
        if (len == null) {
            len = 4;
        }
        if (minute == null) {
            minute = 5;
        }

        // 1. 创建圆圈验证码和设置字体
        CircleCaptcha captcha = CaptchaUtil.createCircleCaptcha(200, 100, len, 20);
        captcha.setFont(new Font("Monospaced", Font.BOLD, 80));

        // 2. 验证码 code 存到 redis
        String code = captcha.getCode();
        stringRedisTemplate.opsForValue().set(str, code, minute, TimeUnit.MINUTES);

        // 3. 将图片转为 base64
        return captcha.getImageBase64();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Boolean> userRegister(UserRequestRequest userRequestRequest) {
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
        Long num = baseMapper.selectCount(new QueryWrapper<User>().eq("username", username));
        ExcUtils.throwIfTrue(num != 0, ExceptionCode.PARAMETER_ERROR, "账号已存在");

        // 密码加盐
        password = DigestUtil.md5Hex(password + SALT);

        // 注册用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(DEFAULT_NICK_NAME + RandomUtil.randomString(6));
        // 默认头像
        user.setAvatar("https://avatars.githubusercontent.com/u/179127403?v=4");
        int insert = baseMapper.insert(user);
        ExcUtils.throwIfTrue(insert != 1, ExceptionCode.DATABASE_ERROR, "注册失败");
        stringRedisTemplate.delete(checkCodeKeyByRegister);
        // 创建默认私人空间
        Boolean spaceRequest = spaceService.createSpace(new CreateSpace(user.getNickname() + "的私人空间", "你的专属私密存储空间", 0),
                user);
        ExcUtils.throwIfTrue(!spaceRequest, ExceptionCode.DATABASE_ERROR, "创建私人空间失败");
        // 删除已注册的验证码
        stringRedisTemplate.delete(checkCodeKeyByRegister);
        return ResUtils.success(true);
    }

    @Override
    public Response<UserLoginVO> userLogin(UserLoginRequest userLoginRequest) {
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
        User user = baseMapper.selectOne(new QueryWrapper<User>().eq("username", username).eq("password", password));
        ExcUtils.throwIfTrue(user == null, ExceptionCode.PARAMETER_ERROR, "账号或密码错误");

        ExcUtils.throwIfTrue(user.getStatus() == null || user.getStatus() != 1, ExceptionCode.PARAMETER_ERROR,
                "账号已被禁用");

        // 签发 JWT
        String jwt = jwtUtils.sign(user.getId());

        // 将用户基本信息写入 Redis（7天）
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserInfoKey(user.getId()),
                JSONUtil.toJsonStr(user),
                RedisConstants.USER_PERM_CTX_TTL, TimeUnit.DAYS);

        // 构建权限上下文并写入 Redis
        LoginContext loginContext = permissionService.buildLoginContext(
                user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatar(), user.getStatus(), user.getLevel());
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserPermCtxKey(user.getId()),
                JSONUtil.toJsonStr(loginContext),
                RedisConstants.USER_PERM_CTX_TTL, TimeUnit.DAYS);

        // 删除已登录的验证码
        stringRedisTemplate.delete(checkCodeKeyByLogin);

        // 返回用户信息
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(user, userLoginVO, CopyOptions.create().setIgnoreProperties("password"));
        userLoginVO.setToken(jwt);
        // 加载用户权限列表（系统权限 + VIP 权限）
        List<String> permissions = new java.util.ArrayList<>(
                loginContext.getSystemPerms() != null ? loginContext.getSystemPerms() : List.of());
        if (loginContext.getVipPerms() != null) {
            permissions.addAll(loginContext.getVipPerms());
        }
        userLoginVO.setPermissions(permissions);
        return ResUtils.success(userLoginVO);
    }

    @Override
    public QueryWrapper<User> newQueryWrapper(UserQueryWrapper userQueryWrapper) {
        Long id = userQueryWrapper.getId();
        String username = userQueryWrapper.getUsername();
        String email = userQueryWrapper.getEmail();
        String phone = userQueryWrapper.getPhone();
        String nickname = userQueryWrapper.getNickname();
        Integer status = userQueryWrapper.getStatus();
        Date createTime = userQueryWrapper.getCreateTime();
        String sortField = userQueryWrapper.getSortField();
        String sortOrder = userQueryWrapper.getSortOrder();

        // 排序字段白名单，防止 SQL 注入
        Set<String> allowedSortFields = Set.of("id", "username", "email", "phone", "nickname", "status", "level", "create_time", "update_time");
        boolean isSortFieldValid = sortField != null && allowedSortFields.contains(sortField);

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjectUtil.isNotNull(id), "id", id);
        queryWrapper.like(ObjectUtil.isNotNull(username), "username", username);
        queryWrapper.like(ObjectUtil.isNotNull(email), "email", email);
        queryWrapper.like(ObjectUtil.isNotNull(phone), "phone", phone);
        queryWrapper.like(ObjectUtil.isNotNull(nickname), "nickname", nickname);
        queryWrapper.eq(ObjectUtil.isNotNull(status), "status", status);
        queryWrapper.eq(ObjectUtil.isNotNull(createTime), "create_time", createTime);

        queryWrapper.orderBy(isSortFieldValid, "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public IPage<AdminGetUserVO> getUserList(UserQueryWrapper userQueryWrapper, long current, long pageSize) {
        ExcUtils.throwIfTrue(current <= 0 || pageSize <= 0, ExceptionCode.PARAMETER_ERROR);
        QueryWrapper<User> queryWrapper = this.newQueryWrapper(userQueryWrapper);
        IPage<User> userPage = baseMapper.selectPage(new Page<>(current, pageSize), queryWrapper);

        // 转换为 AdminGetUserVO，过滤掉密码字段
        return userPage.convert(user -> {
            AdminGetUserVO vo = new AdminGetUserVO();
            BeanUtil.copyProperties(user, vo);
            vo.setRoleIds(permissionService.getUserRoleIds(user.getId()));
            return vo;
        });
    }

    @Override
    public Boolean setStatus(Long userId) {
        User user = baseMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isNull(user), ExceptionCode.NOT_FOUND, "未找到该用户");
        // 超级管理员保护：不能禁用超级管理员
        if (isSuperAdmin(userId)) {
            throw new RuntimeException("不能禁用超级管理员");
        }
        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        int i = baseMapper.updateById(user);
        // 更新redis
        stringRedisTemplate.opsForValue().set(RedisConstants.getUserInfoKey(userId), JSONUtil.toJsonStr(user), 1,
                TimeUnit.DAYS);
        // 清除L1缓存
        cacheManager.getUserInfoCache().evict(String.valueOf(userId));
        // 清除权限缓存
        permissionService.clearUserPermissionCache(userId);
        return i > 0;
    }

    @Override
    public Boolean editUser(UserEditByAdminRequest userEditByAdminRequest) {
        Long id = userEditByAdminRequest.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "用户ID不能为空");

        User user = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isNull(user), ExceptionCode.NOT_FOUND, "未找到该用户");

        if (ObjectUtil.isNotEmpty(userEditByAdminRequest.getPassword())) {
            String encryptPwd = DigestUtil.md5Hex(userEditByAdminRequest.getPassword() + SALT);
            userEditByAdminRequest.setPassword(encryptPwd);
        }

        BeanUtil.copyProperties(userEditByAdminRequest, user,
                CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true));

        int rows = baseMapper.updateById(user);
        ExcUtils.throwIfTrue(rows != 1, ExceptionCode.DATABASE_ERROR, "更新用户失败");

        // 更新用户角色
        List<Long> newRoleIds = userEditByAdminRequest.getRoleIds();
        if (newRoleIds != null) {
            // 超级管理员保护：不能修改超级管理员角色
            if (isSuperAdmin(id) && !newRoleIds.contains(1L)) {
                throw new RuntimeException("不能移除超级管理员角色");
            }
            // 批量删除所有旧角色（一次SQL）
            sysUserRoleMapper.delete(
                    new QueryWrapper<SysUserRole>().eq("user_id", id));
            // 批量插入新角色
            for (Long roleId : newRoleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(id);
                userRole.setRoleId(roleId != null ? roleId.intValue() : null);
                sysUserRoleMapper.insert(userRole);
            }
            // 统一清除权限缓存（一次操作）
            stringRedisTemplate.delete("USER_PERMISSIONS:" + id);
            cacheManager.getUserPermCache().evict(String.valueOf(id));
        }

        String userInfoKey = RedisConstants.getUserInfoKey(id);
        if (stringRedisTemplate.hasKey(userInfoKey)) {
            stringRedisTemplate.delete(userInfoKey);
            User freshUser = baseMapper.selectById(id);
            stringRedisTemplate.opsForValue().set(userInfoKey, JSONUtil.toJsonStr(freshUser), 1,
                    TimeUnit.DAYS);
        }
        // 清除用户信息L1缓存
        cacheManager.getUserInfoCache().evict(String.valueOf(id));

        return true;
    }

    @Override
    public UserMessageVO getMyselfMessage() {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        UserMessageVO vo = new UserMessageVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    public Boolean editMyself(UserEditRequest userEditRequest) {
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
        ExcUtils.throwIfTrue(!this.isMe(id), ExceptionCode.UNAUTHORIZED, "只可修改自己的信息");
        // 查询用户信息
        User user = this.getById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isNull(user), ExceptionCode.DATABASE_ERROR, "用户不存在");

        String oldHashedPassword = user.getPassword();

        // 更新用户信息
        BeanUtil.copyProperties(userEditRequest, user, CopyOptions.create().ignoreNullValue());
        if (StrUtil.isNotBlank(password)) {
            // 校验原始密码
            String originalPassword = userEditRequest.getOriginalPassword();
            ExcUtils.throwIfTrue(StrUtil.isEmpty(originalPassword), ExceptionCode.PARAMETER_ERROR, "请输入初始密码");
            ExcUtils.throwIfTrue(!DigestUtil.md5Hex(originalPassword + SALT).equals(oldHashedPassword),
                    ExceptionCode.PARAMETER_ERROR, "初始密码错误");
            // 密码加盐
            password = DigestUtil.md5Hex(password + SALT);
            user.setPassword(password);
        }
        boolean result = this.updateById(user);
        ExcUtils.throwIfTrue(!result, ExceptionCode.DATABASE_ERROR, "更新失败");
        // 更新 redis 缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.getUserInfoKey(id), JSONUtil.toJsonStr(user), 1,
                TimeUnit.DAYS);
        // 清除L1缓存
        cacheManager.getUserInfoCache().evict(String.valueOf(id));
        return true;
    }

    @Override
    public Boolean updatePrivacy(UserPrivacyRequest request) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        BeanUtil.copyProperties(request, user, CopyOptions.create().ignoreNullValue());
        boolean result = this.updateById(user);
        ExcUtils.throwIfTrue(!result, ExceptionCode.DATABASE_ERROR, "更新失败");
        stringRedisTemplate.opsForValue().set(RedisConstants.getUserInfoKey(user.getId()), JSONUtil.toJsonStr(user), 1,
                TimeUnit.DAYS);
        // 清除L1缓存
        cacheManager.getUserInfoCache().evict(String.valueOf(user.getId()));
        return true;
    }

    @Override
    public Boolean isMe(Long id) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        return user.getId().equals(id);
    }

    /**
     * 判断用户是否为超级管理员
     */
    private boolean isSuperAdmin(Long userId) {
        List<Long> roleIds = permissionService.getUserRoleIds(userId);
        return roleIds.contains(1L); // 超级管理员角色ID=1
    }

    @Override
    public UserPublicProfileVO getUserProfile(Long userId) {
        User currentUser = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(currentUser), ExceptionCode.NOT_LOGIN);

        User targetUser = baseMapper.selectById(userId);
        ExcUtils.throwIfTrue(targetUser == null || targetUser.getId() == null,
                ExceptionCode.NOT_FOUND, "用户不存在");

        boolean isMe = currentUser.getId().equals(userId);

        UserPublicProfileVO vo = new UserPublicProfileVO();
        vo.setId(targetUser.getId());
        vo.setUsername(targetUser.getUsername());
        vo.setNickname(targetUser.getNickname());
        vo.setAvatar(targetUser.getAvatar());
        vo.setCreateTime(targetUser.getCreateTime());

        return vo;
    }

    @Override
    public List<UserSearchVO> searchUsers(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return new ArrayList<>();
        }
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.and(w -> w.like("username", keyword).or().like("nickname", keyword));
        qw.eq("status", 1);
        qw.last("LIMIT 20");
        List<User> users = baseMapper.selectList(qw);
        return users.stream()
                .map(u -> new UserSearchVO(u.getId(), u.getNickname(), u.getAvatar()))
                .collect(Collectors.toList());
    }
}
