package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.user.*;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.common.utils.LoginUser;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.vo.user.CheckCodeVO;
import hk.ljx.fishpicsbackend.vo.user.UserLoginVO;
import hk.ljx.fishpicsbackend.vo.user.UserMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.USER_ID_KEY;
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
    private LoginUser loginUser;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/login")
    public Response<UserLoginVO> userLogin(@RequestBody UserLoginRequest userLoginRequest,
            HttpServletRequest request) {
        ExcUtils.throwIfTrue(userLoginRequest == null, ExceptionCode.PARAMETER_ERROR, "参数错误");
        return userService.userLogin(userLoginRequest, request);
    }

    @PostMapping("/register")
    public Response<Boolean> userRegister(@RequestBody UserRequestRequest userRequestRequest,
            HttpServletRequest request) {
        ExcUtils.throwIfTrue(userRequestRequest == null, "参数不能为空");
        return userService.userRegister(userRequestRequest, request);
    }

    @GetMapping("/checkCode/register")
    public Response<CheckCodeVO> checkCodeRegister() {
        String register = RandomUtil.randomString(10);
        String redisKey = RedisConstants.getRegisterCodeKey(register);
        String base64Image = userService.getCheckCode(redisKey, 5, 5);
        return ResUtils.success(CheckCodeVO.builder()
                .captchaKey(register)
                .base64Image(base64Image)
                .build());
    }

    @GetMapping("/checkCode/login")
    public Response<CheckCodeVO> checkCodeLogin() {
        String login = RandomUtil.randomString(10);
        String redisKey = RedisConstants.getLoginCodeKey(login);
        String base64Image = userService.getCheckCode(redisKey, 5, 5);
        return ResUtils.success(CheckCodeVO.builder()
                .captchaKey(login)
                .base64Image(base64Image)
                .build());
    }

    @GetMapping("/myself")
    public Response<UserMessageVO> getMyself(HttpServletRequest request) {
        UserMessageVO userMessageVO = userService.getMyselfMessage(request);
        return ResUtils.success(userMessageVO);
    }

    @GetMapping("/getUser")
    public Response<UserLoginVO> getUser(HttpServletRequest request) {
        User user = loginUser.getLoginUser(request);
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(user, userLoginVO);
        return ResUtils.success(userLoginVO);
    }

    @PostMapping("/editUser")
    public Response<Boolean> editMyself(@RequestBody UserEditRequest userEditRequest, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userEditRequest), ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.editMyself(userEditRequest, request));
    }

    @PostMapping("/logout")
    public Response<?> logout(HttpServletRequest request) {
        User user = loginUser.getLoginUser(request);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        stringRedisTemplate.delete(USER_ID_KEY + user.getId());
        return ResUtils.success();
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/getUser")
    public Response<User> adminGetUser(@RequestBody UserIdRequest userIdRequest) {
        Long userId = userIdRequest.getUserId();
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userId), ExceptionCode.PARAMETER_ERROR);
        User user = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(user.getId() == null, ExceptionCode.DATABASE_ERROR);
        return ResUtils.success(user);
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/userList")
    public Response<IPage<User>> getUserList(@RequestBody UserQueryWrapper userQueryWrapper) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userQueryWrapper), ExceptionCode.PARAMETER_ERROR);
        long current = userQueryWrapper.getCurrent();
        long pageSize = userQueryWrapper.getPageSize();
        IPage<User> userList = userService.getUserList(userQueryWrapper, current, pageSize);
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
