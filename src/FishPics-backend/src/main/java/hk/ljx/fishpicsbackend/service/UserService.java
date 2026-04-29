package hk.ljx.fishpicsbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.user.*;
import hk.ljx.fishpicsbackend.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.vo.user.UserLoginVO;
import hk.ljx.fishpicsbackend.vo.user.UserMessageVO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
* @author 30574
* 针对表【user(用户表)】的数据库操作Service
* 2026-04-13 21:24:26
*/
public interface UserService extends IService<User> {

    /**
     * 获取验证码
     * @param str 验证码前缀
     * @param len 验证码长度
     * @param minute 验证码有效期 - 分钟
     * @return base64 图片
     */
    String getCheckCode(String str, Integer len, Integer minute);

    /**
     * 系统内获取当前登录用户
     * @param request 请求信息
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

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录请求
     * @return 用户 VO
     */
    Response<UserLoginVO> userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 构造查询用户条件
     *
     * @param userQueryWrapper 用户查询条件
     * @return 查询条件
     */
    QueryWrapper<User> newQueryWrapper(UserQueryWrapper userQueryWrapper);

    /**
     * 管理员获取用户列表
     *
     * @return 用户列表
     */
    IPage<User> getUserList(UserQueryWrapper userQueryWrapper, long current, long pageSize);

    /**
     * 管理员设置用户状态（1正常，0封禁）
     *
     * @param userId 用户id
     * @return 设置结果
     */
    Boolean setStatus(Long userId);

    /**
     * 管理员编辑用户信息
     *
     * @param userEditByAdminRequest 用户信息
     * @return 编辑结果
     */
    Boolean editUser(UserEditByAdminRequest userEditByAdminRequest);

    /**
     * 获取自己的主页信息
     * @param request 登录token
     * @return 用户信息
     */
    UserMessageVO getMyselfMessage(HttpServletRequest request);

    /**
     * 用户编辑自己信息
     *
     * @param userEditRequest 用户信息
     * @return 编辑结果
     */
    Boolean editMyself(UserEditRequest userEditRequest, HttpServletRequest request);

    /**
     * 判断是否是自己的信息
     * @param id 用户id
     * @param request 登录token
     * @return 是否是自己的信息
     */
    Boolean isMe(Long id, HttpServletRequest request);
}
