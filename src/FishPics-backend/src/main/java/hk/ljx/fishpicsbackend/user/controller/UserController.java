package hk.ljx.fishpicsbackend.user.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.constants.UserConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
    public Response<UserVO> userLogin(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        ExcUtils.throwIfTrue(userLoginRequest == null, ExceptionCode.PARAMETER_ERROR, "参数错误");
        return userService.userLogin(userLoginRequest);
    }

    @PostMapping("/register")
    public Response<Boolean> userRegister(@Valid @RequestBody UserRequestRequest userRequestRequest) {
        ExcUtils.throwIfTrue(userRequestRequest == null, "参数不能为空");
        return userService.userRegister(userRequestRequest);
    }

    @GetMapping("/checkCode/register")
    public Response<CheckCodeVO> checkCodeRegister() {
        String register = UUID.randomUUID().toString(true);
        String redisKey = RedisConstants.getRegisterCodeKey(register);
        String base64Image = userService.getCheckCode(redisKey, 5, 5);
        return Response.ok(CheckCodeVO.builder()
                .captchaKey(register)
                .base64Image(UserConstants.getCheckCode(base64Image))
                .build());
    }

    @GetMapping("/checkCode/login")
    public Response<CheckCodeVO> checkCodeLogin() {
        String login = UUID.randomUUID().toString(true);
        String redisKey = RedisConstants.getLoginCodeKey(login);
        String base64Image = userService.getCheckCode(redisKey, 5, 5);
        return Response.ok(CheckCodeVO.builder()
                .captchaKey(login)
                .base64Image(UserConstants.getCheckCode(base64Image))
                .build());
    }

    @GetMapping("/myself")
    public Response<UserVO> getMyself() {
        UserVO userVO = userService.getMyselfMessage();
        return Response.ok(userVO);
    }

    @Resource
    private hk.ljx.fishpicsbackend.common.utils.JwtUtils jwtUtils;

    /**
     * 获取当前登录用户信息（从 Redis 缓存的 LoginContext 中读取，不查库）
     */
    @GetMapping("/getUser")
    public Response<UserVO> getUser() {
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN);

        // 权限列表 = 系统权限 + VIP 权限
        List<String> allPerms = new java.util.ArrayList<>(
                ctx.getSystemPerms() != null ? ctx.getSystemPerms() : java.util.List.of());
        if (ctx.getVipPerms() != null) {
            allPerms.addAll(ctx.getVipPerms());
        }

        UserVO userVO = UserVO.builder()
                .id(ctx.getUserId())
                .username(ctx.getUsername())
                .nickname(ctx.getNickname())
                .avatar(ctx.getAvatar())
                .level(ctx.getLevel())
                .permissions(allPerms)
                .build();

        return Response.ok(userVO);
    }

    @PostMapping("/editUser")
    public Response<Boolean> editMyself(@Valid @RequestBody UserEditRequest userEditRequest,
                                        HttpServletRequest request, HttpServletResponse response) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userEditRequest), ExceptionCode.PARAMETER_ERROR);
        // 修复:在 controller 捕获当前 JWT(在拦截器清理 LoginContext 之前仍可读 header),
        // 改完密码后只黑名单当前 token 而非踢所有 token;同时下发新 token 让前端无感续期
        String authHeader = request.getHeader("Authorization");
        String currentJwt = null;
        if (StrUtil.isNotBlank(authHeader)) {
            currentJwt = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        }
        Boolean result = userService.editMyself(userEditRequest, currentJwt);
        // 改完密码 → 给前端下发新 token(走 X-New-Token header,前端 axios 拦截器已自动 saveToken)
        if (Boolean.TRUE.equals(result) && userEditRequest.getPassword() != null
                && !userEditRequest.getPassword().isEmpty()) {
            UserVO me = userService.getMyselfMessage();
            String newJwt = jwtUtils.sign(me.getId());
            response.setHeader("X-New-Token", newJwt);
        }
        return Response.ok(result);
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
            //    仅在 JWT 未过期时清理，防止过期 JWT 被利用强制他人登出
            if (!jwtUtils.isExpired(jwt)) {
                Long userId = jwtUtils.getUserId(jwt);
                if (userId != null) {
                    stringRedisTemplate.delete(RedisConstants.getUserPermCtxKey(userId));
                }
            }
        }
        UserHolder.removeLoginContext();
        return Response.ok();
    }

    @GetMapping("/profile")
    public Response<UserVO> getUserProfile(@RequestParam Long userId) {
        ExcUtils.throwIfTrue(userId == null, ExceptionCode.PARAMETER_ERROR);
        return Response.ok(userService.getUserProfile(userId));
    }

    @GetMapping("/search")
    public Response<List<UserVO>> searchUsers(@RequestParam(required = false) String keyword) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        return Response.ok(userService.searchUsers(keyword));
    }

    @RequireAdmin
    @AuditLog(module = "用户管理", operation = "查询用户详情")
    @PostMapping("/admin/getUser")
    public Response<UserVO> adminGetUser(@Valid @RequestBody UserIdRequest userIdRequest) {
        Long userId = userIdRequest.getUserId();
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userId), ExceptionCode.PARAMETER_ERROR);
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_FOUND, "用户不存在");

        UserVO userVO = UserVO.ofAdmin(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getLevel(),
                user.getCreateTime(),
                null  // 不再需要 roleIds，使用 level 判断权限
        );

        return Response.ok(userVO);
    }

    @RequireAdmin
    @PostMapping("/admin/userList")
    public Response<IPage<UserVO>> getUserList(@Valid @RequestBody UserQueryWrapper userQueryWrapper) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userQueryWrapper), ExceptionCode.PARAMETER_ERROR);
        long current = userQueryWrapper.getCurrent();
        long pageSize = userQueryWrapper.getPageSize();
        IPage<UserVO> userList = userService.getUserList(userQueryWrapper, current, pageSize);
        return Response.ok(userList);
    }

    @RequireAdmin
    @PostMapping("/admin/setStatus")
    @AuditLog(module = "用户管理", operation = "用户状态变更")
    public Response<Boolean> setStatus(@Valid @RequestBody UserIdRequest userIdRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userIdRequest), ExceptionCode.PARAMETER_ERROR);
        Long userId = userIdRequest.getUserId();
        return Response.ok(userService.setStatus(userId));
    }

    @RequireAdmin
    @PostMapping("/admin/editUser")
    @AuditLog(module = "用户管理", operation = "编辑用户")
    public Response<Boolean> editUser(@Valid @RequestBody UserEditByAdminRequest userEditByAdminRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userEditByAdminRequest), ExceptionCode.PARAMETER_ERROR);
        return Response.ok(userService.editUser(userEditByAdminRequest));
    }
}
