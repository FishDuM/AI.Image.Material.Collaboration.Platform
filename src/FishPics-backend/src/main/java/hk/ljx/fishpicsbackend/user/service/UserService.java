package hk.ljx.fishpicsbackend.user.service;
import hk.ljx.fishpicsbackend.user.entity.User;

import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.user.dto.*;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.user.vo.UserVO;

import java.util.List;

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
     * 用户注册
     *
     * @param userRequestRequest 用户注册请求
     * @return 注册结果
     */
    Response<Boolean> userRegister(UserRequestRequest userRequestRequest);

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录请求
     * @return 用户 VO
     */
    Response<UserVO> userLogin(UserLoginRequest userLoginRequest);

    /**
     * 管理员获取用户列表
     *
     * @return 用户列表
     */
    IPage<UserVO> getUserList(UserQueryWrapper userQueryWrapper, long current, long pageSize);

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
     * @return 用户信息
     */
    UserVO getMyselfMessage();

    /**
     * 用户编辑自己信息
     *
     * @param userEditRequest 用户信息
     * @param currentJwt     当前请求的 JWT(改密码时只黑名单当前 token,避免把同用户其他设备也踢下线)
     * @return 编辑结果
     */
    Boolean editMyself(UserEditRequest userEditRequest, String currentJwt);

    /**
     * 判断是否是自己的信息
     * @param id 用户id
     * @return 是否是自己的信息
     */
    Boolean isMe(Long id);

    /**
     * 获取用户公开主页信息
     * @param userId 目标用户ID
     * @return 用户公开主页VO
     */
    UserVO getUserProfile(Long userId);

    /**
     * 按用户名或昵称搜索用户
     * @param keyword 搜索关键词
     * @return 匹配的用户列表
     */
    List<UserVO> searchUsers(String keyword);
}
