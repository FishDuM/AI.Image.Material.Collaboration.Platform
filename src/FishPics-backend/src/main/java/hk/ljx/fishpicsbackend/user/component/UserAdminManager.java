package hk.ljx.fishpicsbackend.user.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.lang.Validator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.enums.Role;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.PasswordUtil;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.user.dto.UserEditByAdminRequest;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.NICKNAME_MAX_LENGTH;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.NICKNAME_MIN_LENGTH;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.PASSWORD_MAX_LENGTH;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.PASSWORD_MIN_LENGTH;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.USERNAME_MAX_LENGTH;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.USERNAME_MIN_LENGTH;

@Component
@Slf4j
public class UserAdminManager {

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserCacheManager userCacheManager;

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
        // 事务提交后再清缓存，失败时管理员看到错误并重试
        Long finalUserId = userId;
        int finalNewStatus = newStatus;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    userCacheManager.refreshUserInfoCache(user);
                    userCacheManager.invalidateUserTokens(finalUserId);
                    if (finalNewStatus == 0) {
                        stringRedisTemplate.opsForSet().add(RedisConstants.BANNED_USERS_KEY, finalUserId.toString());
                    } else {
                        stringRedisTemplate.opsForSet().remove(RedisConstants.BANNED_USERS_KEY, finalUserId.toString());
                    }
                } catch (Exception e) {
                    log.error("[UserAdminManager] cache/token cleanup failed after setStatus: userId={}, error={}",
                            finalUserId, e.getMessage(), e);
                    // 事务已提交，不抛异常，避免 HTTP 响应失败（数据库变更已生效）
                }
            }
        });
        return true;
    }

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
            int roleRows = userMapper.updateRoleIfNotLastAdmin(originalId);
            ExcUtils.throwIfTrue(roleRows != 1, ExceptionCode.FORBIDDEN,
                    "系统至少需要保留一名管理员,无法降级最后一名 admin");
            user.setRole(Role.NORMAL.getCode());
            rows = userMapper.updateById(user);
        } else {
            rows = userMapper.updateById(user);
        }
        ExcUtils.throwIfTrue(rows != 1, ExceptionCode.DATABASE_ERROR, "更新用户失败");

        userCacheManager.refreshUserInfoCache(user);
        userCacheManager.evictUserLoginContext(id);
        if (passwordChanged) {
            userCacheManager.invalidateUserTokens(id);
        }
        log.info("[UserAdminManager] admin editUser cleanup: id={}, passwordChanged={}", id, passwordChanged);
        return true;
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
                    .eq(User::getRole, Role.ADMIN.getCode())
                    .eq(User::getStatus, 1));
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
                    .eq(User::getUsername, request.getUsername())
                    .ne(User::getId, request.getId()));
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
}
