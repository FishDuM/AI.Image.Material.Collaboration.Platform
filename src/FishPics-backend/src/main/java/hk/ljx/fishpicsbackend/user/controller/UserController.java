package hk.ljx.fishpicsbackend.user.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.annotation.RequireLogin;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.constants.UserConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.infra.JwtUtils;
import hk.ljx.fishpicsbackend.common.infra.RateLimiter;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.user.dto.*;
import hk.ljx.fishpicsbackend.user.service.UserService;
import hk.ljx.fishpicsbackend.user.vo.*;

import java.util.List;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private RedisCacheManager cacheManager;

    @Resource
    private RateLimiter rateLimiter;

    @PostMapping("/login")
    public Response<UserVO> userLogin(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        rateLimiter.acquire("login:" + userLoginRequest.getUsername(), 5, 60);
        return userService.userLogin(userLoginRequest);
    }

    @PostMapping("/register")
    public Response<Boolean> userRegister(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {
        rateLimiter.acquire("register:" + userRegisterRequest.getUsername(), 3, 300);
        return userService.userRegister(userRegisterRequest);
    }

    @GetMapping("/checkCode/register")
    public Response<CheckCodeVO> checkCodeRegister() {
        return Response.ok(doCheckCode("register"));
    }

    @GetMapping("/checkCode/login")
    public Response<CheckCodeVO> checkCodeLogin() {
        return Response.ok(doCheckCode("login"));
    }

    private CheckCodeVO doCheckCode(String type) {
        String key = UUID.randomUUID().toString(true);
        String redisKey = "register".equals(type)
                ? RedisConstants.getRegisterCodeKey(key)
                : RedisConstants.getLoginCodeKey(key);
        String base64Image = userService.getCheckCode(redisKey, 5, 5);
        return CheckCodeVO.builder()
                .captchaKey(key)
                .base64Image(UserConstants.getCheckCode(base64Image))
                .build();
    }

    @RequireLogin
    @GetMapping("/myself")
    public Response<UserVO> getMyself() {
        UserVO userVO = userService.getMyselfMessage();
        return Response.ok(userVO);
    }

    @Resource
    private JwtUtils jwtUtils;

    @RequireLogin
    @GetMapping("/getUser")
    public Response<UserVO> getUser() {
        return Response.ok(userService.getCurrentUserVO());
    }

    @RequireLogin
    @PostMapping("/editUser")
    public Response<Boolean> editMyself(@Valid @RequestBody UserEditRequest userEditRequest,
                                        HttpServletRequest request, HttpServletResponse response) {
        String currentJwt = JwtUtils.extractJwt(request);
        Boolean result = userService.editMyself(userEditRequest, currentJwt);
        if (Boolean.TRUE.equals(result) && userEditRequest.getPassword() != null
                && !userEditRequest.getPassword().isEmpty()) {
            UserVO me = userService.getMyselfMessage();
            String newJwt = jwtUtils.sign(me.getId());
            response.setHeader("X-New-Token", newJwt);
        }
        return Response.ok(result);
    }

    @RequireLogin
    @AuditLog(module = "用户管理", operation = "用户登出")
    @PostMapping("/logout")
    public Response<?> logout(HttpServletRequest request) {
        String jwt = JwtUtils.extractJwt(request);
        if (jwt != null) {
            try {
                jwtUtils.addToBlacklist(jwt);
            } catch (Exception e) {
                log.warn("addToBlacklist 失败(Redis 故障,token 将自然过期): err={}", e.getMessage());
            }
            if (!jwtUtils.isExpired(jwt)) {
                Long userId = jwtUtils.getUserId(jwt);
                if (userId != null) {
                    try {
                        cacheManager.getUserPermCache().evict(String.valueOf(userId));
                    } catch (Exception e) {
                        log.warn("删除用户会话缓存失败(Redis 故障): userId={}, err={}", userId, e.getMessage());
                    }
                }
            }
        }
        UserHolder.removeLoginContext();
        return Response.ok();
    }

    @RequireLogin
    @GetMapping("/profile")
    public Response<UserVO> getUserProfile(@RequestParam Long userId) {
        ExcUtils.throwIfTrue(userId == null, ExceptionCode.PARAMETER_ERROR);
        return Response.ok(userService.getUserProfile(userId));
    }

    @RequireLogin
    @GetMapping("/search")
    public Response<List<UserVO>> searchUsers(@RequestParam(required = false) String keyword) {
        return Response.ok(userService.searchUsers(keyword));
    }

    @RequireAdmin
    @AuditLog(module = "用户管理", operation = "查询用户详情")
    @PostMapping("/admin/getUser")
    public Response<UserVO> adminGetUser(@Valid @RequestBody UserIdRequest userIdRequest) {
        Long userId = userIdRequest.getUserId();
        return Response.ok(userService.adminGetUser(userId));
    }

    @RequireAdmin
    @PostMapping("/admin/getUserDetail")
    public Response<UserVO> adminGetUserDetail(@Valid @RequestBody UserIdRequest userIdRequest) {
        Long userId = userIdRequest.getUserId();
        return Response.ok(userService.adminGetUserDetail(userId));
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
