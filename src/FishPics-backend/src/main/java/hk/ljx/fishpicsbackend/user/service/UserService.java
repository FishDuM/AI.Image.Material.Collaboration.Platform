package hk.ljx.fishpicsbackend.user.service;
import hk.ljx.fishpicsbackend.user.entity.User;

import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.user.dto.*;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.user.vo.UserVO;

import java.util.List;

public interface UserService extends IService<User> {

    String getCheckCode(String str, Integer len, Integer minute);

    Response<Boolean> userRegister(UserRegisterRequest userRegisterRequest);

    Response<UserVO> userLogin(UserLoginRequest userLoginRequest);

    IPage<UserVO> getUserList(UserQueryWrapper userQueryWrapper, long current, long pageSize);

    // 1正常 0封禁，来回切
    Boolean setStatus(Long userId);

    Boolean editUser(UserEditByAdminRequest userEditByAdminRequest);

    UserVO getMyselfMessage();

    // currentJwt 改密码时用，只黑名单当前 token，不踢其他设备
    Boolean editMyself(UserEditRequest userEditRequest, String currentJwt);

    Boolean isMe(Long id);

    UserVO getUserProfile(Long userId);

    List<UserVO> searchUsers(String keyword);

    // 改了昵称/头像后调一下，刷新 Redis 缓存
    void refreshUserInfoCache(User user);

    // 管理端查询用户详情（带脱敏）
    UserVO adminGetUser(Long userId);

    // 从 LoginContext 构建当前用户 VO（合并系统权限+VIP权限）
    UserVO getCurrentUserVO();
}
