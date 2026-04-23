package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.RandomUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.user.UserLoginRequest;
import hk.ljx.fishpicsbackend.dto.user.UserRequestRequest;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.vo.CheckCodeVO;
import hk.ljx.fishpicsbackend.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.LOGIN_CODE_KEY;

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
}
