package hk.ljx.fishpicsbackend.controller;

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
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.vo.CheckCodeVO;
import hk.ljx.fishpicsbackend.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/login")
    public Response<UserLoginVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletResponse response,
            HttpServletRequest request) {
        ExcUtils.throwIfTrue(userLoginRequest == null, ExceptionCode.PARAMETER_ERROR, "参数错误");
        return userService.userLogin(userLoginRequest, response, request);
    }

    @PostMapping("/register")
    public Response<Boolean> userRegister(@RequestBody UserRequestRequest userRequestRequest,
            HttpServletRequest request, HttpServletResponse response) {
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

//    @PostMapping("/user/myself")
//    public Response<User> getMyself(@RequestBody UserIdRequest userIdRequest) {
//        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userIdRequest), ExceptionCode.PARAMETER_ERROR);
//        UserLoginVO userLoginVO = userService.getUserMessage(userIdRequest);
//
//    }


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
    public Response<Boolean> editUser(@RequestBody UserEditRequest userEditRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isNull(userEditRequest), ExceptionCode.PARAMETER_ERROR);
        return ResUtils.success(userService.editUser(userEditRequest));
    }
}
