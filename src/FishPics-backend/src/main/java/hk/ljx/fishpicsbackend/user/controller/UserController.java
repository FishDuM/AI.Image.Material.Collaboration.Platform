package hk.ljx.fishpicsbackend.user.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
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
import hk.ljx.fishpicsbackend.user.service.UserFansService;
import hk.ljx.fishpicsbackend.user.service.UserService;
import hk.ljx.fishpicsbackend.user.vo.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

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

    @Resource
    private UserFansService userFansService;

    @PostMapping("/login")
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

    @GetMapping("/getUser")
    public Response<UserLoginVO> getUser() {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(user, userLoginVO);
        return ResUtils.success(userLoginVO);
    }

    @PostMapping("/editUser")
    public Response<Boolean> editMyself(@RequestBody UserEditRequest userEditRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userEditRequest), ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.editMyself(userEditRequest));
    }

    @PostMapping("/logout")
    public Response<?> logout(HttpServletRequest request) {
        UserHolder.removeUser();
        String token = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(RedisConstants.getUserIdKey(token));
        }
        return ResUtils.success();
    }

    @PostMapping("/privacy")
    public Response<Boolean> updatePrivacy(@RequestBody UserPrivacyRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(request), ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.updatePrivacy(request));
    }

    @PostMapping("/follow")
    public Response<Boolean> follow(@RequestBody UserIdRequest userIdRequest) {
        ExcUtils.throwIfTrue(userIdRequest == null || userIdRequest.getUserId() == null,
                ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userFansService.follow(userIdRequest.getUserId()));
    }

    @GetMapping("/fans")
    public Response<IPage<FollowUserVO>> getFans(@RequestParam(required = false) Long userId,
                                                  @RequestParam(defaultValue = "1") int current,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        Long queryUserId = userId != null ? userId : UserHolder.getUser().getId();
        return ResUtils.success(userFansService.getFans(queryUserId, current, pageSize));
    }

    @GetMapping("/follows")
    public Response<IPage<FollowUserVO>> getFollows(@RequestParam(required = false) Long userId,
                                                     @RequestParam(defaultValue = "1") int current,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        Long queryUserId = userId != null ? userId : UserHolder.getUser().getId();
        return ResUtils.success(userFansService.getFollows(queryUserId, current, pageSize));
    }

    @GetMapping("/profile")
    public Response<UserPublicProfileVO> getUserProfile(@RequestParam Long userId) {
        ExcUtils.throwIfTrue(userId == null, ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.getUserProfile(userId));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/getUser")
    public Response<AdminGetUserVO> adminGetUser(@RequestBody UserIdRequest userIdRequest) {
        Long userId = userIdRequest.getUserId();
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userId), ExceptionCode.PARAMETER_ERROR);
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user) || user.getId() == null, ExceptionCode.DATABASE_ERROR);
        AdminGetUserVO adminGetUserVO = BeanUtil.copyProperties(user, AdminGetUserVO.class);
        return ResUtils.success(adminGetUserVO);
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/userList")
    public Response<IPage<AdminGetUserVO>> getUserList(@RequestBody UserQueryWrapper userQueryWrapper) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userQueryWrapper), ExceptionCode.PARAMETER_ERROR);
        long current = userQueryWrapper.getCurrent();
        long pageSize = userQueryWrapper.getPageSize();
        IPage<AdminGetUserVO> userList = userService.getUserList(userQueryWrapper, current, pageSize);
        return ResUtils.success(userList);
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/setStatus")
    public Response<Boolean> setStatus(@RequestBody UserIdRequest userIdRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userIdRequest), ExceptionCode.PARAMETER_ERROR);
        Long userId = userIdRequest.getUserId();
        return ResUtils.success(userService.setStatus(userId));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/editUser")
    public Response<Boolean> editUser(@RequestBody UserEditByAdminRequest userEditByAdminRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userEditByAdminRequest), ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.editUser(userEditByAdminRequest));
    }
}
