package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.RandomUtil;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.user.UserRequestRequest;
import hk.ljx.fishpicsbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/login")
    public String userLogin(){
        return "login";
    }

    @PostMapping("/register")
    public Response<Boolean> userRegister(@RequestBody UserRequestRequest userRequestRequest, HttpServletRequest request, HttpServletResponse response) {
        ExcUtils.throwIfTrue(userRequestRequest == null, "参数不能为空");
        return userService.userRegister(userRequestRequest, request);
    }

    @GetMapping("/checkCode/register")
    public Response<Boolean> checkCodeRegister(HttpServletRequest request, HttpServletResponse response){
        String registerKey = RandomUtil.randomString(10);
        request.getSession().setAttribute("register", registerKey);
        userService.getCheckCode(RedisConstants.getCheckCodeKeyByRegister(registerKey), 5, 5, response);
        return ResUtils.success(true);
    }
}
