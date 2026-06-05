package hk.ljx.fishpicsbackend.user.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequirePerm;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.constants.UserConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.user.dto.*;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import hk.ljx.fishpicsbackend.user.vo.*;

import java.util.List;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/login")
    @AuditLog(module = "用户管理", operation = "用户登录")
    public Response<UserLoginVO> userLogin(@RequestBody UserLoginRequest userLoginRequest) {
        ExcUtils.throwIfTrue(userLoginRequest == null, ExceptionCode.PARAMETER_ERROR, "参数错误");
        return userService.userLogin(userLoginRequest);
    }

    @PostMapping("/register")
    public Response<Boolean> userRegister(@RequestBody UserRequestRequest userRequestRequest) {
        ExcUtils.throwIfTrue(userRequestRequest == null, "参数不能为空");
        return userService.userRegister(userRequestRequest);
    }

    @GetMapping("/checkCode/register")
    public Response<CheckCodeVO> checkCodeRegister() {
        String register = UUID.randomUUID().toString(true);
        String redisKey = RedisConstants.getRegisterCodeKey(register);
        String base64Image = userService.getCheckCode(redisKey, 5, 5);
        return ResUtils.success(CheckCodeVO.builder()
                .captchaKey(register)
                .base64Image(UserConstants.getCheckCode(base64Image))
                .build());
    }

    @GetMapping("/checkCode/login")
    public Response<CheckCodeVO> checkCodeLogin() {
        String login = RandomUtil.randomString(10);
        String redisKey = RedisConstants.getLoginCodeKey(login);
        String base64Image = userService.getCheckCode(redisKey, 5, 5);
        return ResUtils.success(CheckCodeVO.builder()
                .captchaKey(login)
                .base64Image(UserConstants.getCheckCode(base64Image))
                .build());
    }

    @GetMapping("/myself")
    public Response<UserMessageVO> getMyself() {
        UserMessageVO userMessageVO = userService.getMyselfMessage();
        return ResUtils.success(userMessageVO);
    }

    @Resource
    private hk.ljx.fishpicsbackend.common.utils.JwtUtils jwtUtils;

    @Resource
    private hk.ljx.fishpicsbackend.permission.service.PermissionService permissionService;

    @GetMapping("/getUser")
    public Response<UserLoginVO> getUser() {
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN);
        UserLoginVO userLoginVO = new UserLoginVO();
        userLoginVO.setId(ctx.getUserId());
        userLoginVO.setUsername(ctx.getUsername());
        userLoginVO.setNickname(ctx.getNickname());
        userLoginVO.setAvatar(ctx.getAvatar());
        userLoginVO.setLevel(ctx.getLevel());
        // 权限列表 = 系统权限 + VIP 权限
        List<String> allPerms = new java.util.ArrayList<>(
                ctx.getSystemPerms() != null ? ctx.getSystemPerms() : java.util.List.of());
        if (ctx.getVipPerms() != null) {
            allPerms.addAll(ctx.getVipPerms());
        }
        userLoginVO.setPermissions(allPerms);
        return ResUtils.success(userLoginVO);
    }

    @PostMapping("/editUser")
    public Response<Boolean> editMyself(@RequestBody UserEditRequest userEditRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userEditRequest), ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.editMyself(userEditRequest));
    }

    @PostMapping("/logout")
    @AuditLog(module = "用户管理", operation = "用户登出")
    public Response<?> logout(HttpServletRequest request) {
        // 1. 将 JWT 加入黑名单
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(authHeader)) {
            String jwt = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            jwtUtils.addToBlacklist(jwt);
            // 2. 从 JWT 中获取 userId 并删除 Redis 会话
            Long userId = jwtUtils.getUserId(jwt);
            if (userId != null) {
                stringRedisTemplate.delete(RedisConstants.getUserPermCtxKey(userId));
            }
        }
        UserHolder.removeLoginContext();
        return ResUtils.success();
    }

    @PostMapping("/privacy")
    public Response<Boolean> updatePrivacy(@RequestBody UserPrivacyRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(request), ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.updatePrivacy(request));
    }

    @GetMapping("/profile")
    public Response<UserPublicProfileVO> getUserProfile(@RequestParam Long userId) {
        ExcUtils.throwIfTrue(userId == null, ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.getUserProfile(userId));
    }

    @GetMapping("/search")
    public Response<List<UserSearchVO>> searchUsers(@RequestParam(required = false) String keyword) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        return ResUtils.success(userService.searchUsers(keyword));
    }

    @RequirePerm("system:user:manage")
    @PostMapping("/admin/getUser")
    public Response<AdminGetUserVO> adminGetUser(@RequestBody UserIdRequest userIdRequest) {
        Long userId = userIdRequest.getUserId();
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userId), ExceptionCode.PARAMETER_ERROR);
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, ExceptionCode.DATABASE_ERROR);
        AdminGetUserVO adminGetUserVO = BeanUtil.copyProperties(user, AdminGetUserVO.class);
        adminGetUserVO.setRoleIds(permissionService.getUserRoleIds(userId));
        return ResUtils.success(adminGetUserVO);
    }

    @RequirePerm("system:user:manage")
    @PostMapping("/admin/userList")
    public Response<IPage<AdminGetUserVO>> getUserList(@RequestBody UserQueryWrapper userQueryWrapper) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userQueryWrapper), ExceptionCode.PARAMETER_ERROR);
        long current = userQueryWrapper.getCurrent();
        long pageSize = userQueryWrapper.getPageSize();
        IPage<AdminGetUserVO> userList = userService.getUserList(userQueryWrapper, current, pageSize);
        return ResUtils.success(userList);
    }

    @RequirePerm("system:user:manage")
    @PostMapping("/admin/setStatus")
    @AuditLog(module = "用户管理", operation = "用户状态变更")
    public Response<Boolean> setStatus(@RequestBody UserIdRequest userIdRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userIdRequest), ExceptionCode.PARAMETER_ERROR);
        Long userId = userIdRequest.getUserId();
        return ResUtils.success(userService.setStatus(userId));
    }

    @RequirePerm("system:user:manage")
    @PostMapping("/admin/editUser")
    @AuditLog(module = "用户管理", operation = "编辑用户")
    public Response<Boolean> editUser(@RequestBody UserEditByAdminRequest userEditByAdminRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userEditByAdminRequest), ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.editUser(userEditByAdminRequest));
    }
}
