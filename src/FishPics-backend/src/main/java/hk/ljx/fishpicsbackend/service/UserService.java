package hk.ljx.fishpicsbackend.service;

import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.user.UserRequestRequest;
import hk.ljx.fishpicsbackend.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
* @author 30574
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2026-04-13 21:24:26
*/
public interface UserService extends IService<User> {

    /**
     * 获取验证码
     * @param str 验证码前缀
     * @param len 验证码长度
     * @param minute 验证码有效期 - 分钟
     * @param response response
     */
    void getCheckCode(String str, Integer len, Integer minute, HttpServletResponse response);

    /**
     * 系统内获取当前登录用户
     * @param request request
     * @return 用户实体
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注册
     *
     * @param userRequestRequest 用户注册请求
     * @return 注册结果
     */
    Response<Boolean> userRegister(UserRequestRequest userRequestRequest, HttpServletRequest request);
}
