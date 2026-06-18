package hk.ljx.fishpicsbackend.user.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
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

import java.util.Objects;

@Component
@Slf4j
public class UserAdminManager {

    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 32;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserCacheManager userCacheManager;

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
        try {
            userCacheManager.refreshUserInfoCache(user);
            userCacheManager.invalidateUserTokens(userId);
            if (newStatus == 0) {
                stringRedisTemplate.opsForSet().add(RedisConstants.BANNED_USERS_KEY, userId.toString());
            } else {
                stringRedisTemplate.opsForSet().remove(RedisConstants.BANNED_USERS_KEY, userId.toString());
            }
        } catch (Exception e) {
            log.error("[UserAdminManager] cache/token cleanup failed after setStatus: userId={}, error={}",
                    userId, e.getMessage());
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean editUser(UserEditByAdminRequest request) {
        Long id = request.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "用户 ID 不能为空");

        User currentOperator = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(Objects.equals(currentOperator.getId(), id),
                ExceptionCode.FORBIDDEN, "管理员不能通过此接口修改自己,请使用 /user/editUser");

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

        try {
            userCacheManager.refreshUserInfoCache(user);
            userCacheManager.evictUserLoginContext(id);
            userCacheManager.invalidateUserTokens(id);
            log.info("[UserAdminManager] admin editUser cleanup: id={}, passwordChanged={}", id, passwordChanged);
        } catch (Exception e) {
            log.warn("[UserAdminManager] admin editUser cache cleanup failed: id={}", id, e);
        }
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

    private void cleanAdminEditRequest(UserEditByAdminRequest request) {
        request.setUsername(cleanPlain(request.getUsername()));
        request.setNickname(cleanPlain(request.getNickname()));
        request.setEmail(cleanPlain(request.getEmail()));
        request.setPhone(cleanPlain(request.getPhone()));
    }

    private String cleanPlain(String value) {
        return StrUtil.isBlank(value) ? value : XssSanitizer.clean(value);
    }
}
