package hk.ljx.fishpicsbackend.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.user.component.CaptchaManager;
import hk.ljx.fishpicsbackend.user.component.UserAdminManager;
import hk.ljx.fishpicsbackend.user.component.UserCacheManager;
import hk.ljx.fishpicsbackend.user.component.UserManager;
import hk.ljx.fishpicsbackend.user.component.UserQueryManager;
import hk.ljx.fishpicsbackend.user.dto.UserEditByAdminRequest;
import hk.ljx.fishpicsbackend.user.dto.UserEditRequest;
import hk.ljx.fishpicsbackend.user.dto.UserLoginRequest;
import hk.ljx.fishpicsbackend.user.dto.UserQueryWrapper;
import hk.ljx.fishpicsbackend.user.dto.UserRegisterRequest;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import hk.ljx.fishpicsbackend.user.vo.UserVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private CaptchaManager captchaManager;

    @Resource
    private UserManager userManager;

    @Resource
    private UserQueryManager userQueryManager;

    @Resource
    private UserAdminManager userAdminManager;

    @Resource
    private UserCacheManager userCacheManager;

    @Override
    public String getCheckCode(String str, Integer len, Integer minute) {
        return captchaManager.getCheckCode(str, len, minute);
    }

    @Override
    public Response<Boolean> userRegister(UserRegisterRequest userRegisterRequest) {
        return userManager.userRegister(userRegisterRequest);
    }

    @Override
    public Response<UserVO> userLogin(UserLoginRequest userLoginRequest) {
        return userManager.userLogin(userLoginRequest);
    }

    @Override
    public IPage<UserVO> getUserList(UserQueryWrapper userQueryWrapper, long current, long pageSize) {
        return userQueryManager.getUserList(userQueryWrapper, current, pageSize);
    }

    @Override
    public Boolean setStatus(Long userId) {
        return userAdminManager.setStatus(userId);
    }

    @Override
    public Boolean editUser(UserEditByAdminRequest userEditByAdminRequest) {
        return userAdminManager.editUser(userEditByAdminRequest);
    }

    @Override
    public Boolean editMyself(UserEditRequest userEditRequest, String currentJwt) {
        return userManager.editMyself(userEditRequest, currentJwt);
    }

    @Override
    public Boolean isMe(Long id) {
        return userQueryManager.isMe(id);
    }

    @Override
    public UserVO getMyselfMessage() {
        return userQueryManager.getMyselfMessage();
    }

    @Override
    public UserVO getUserProfile(Long userId) {
        return userQueryManager.getUserProfile(userId);
    }

    @Override
    public List<UserVO> searchUsers(String keyword) {
        return userQueryManager.searchUsers(keyword);
    }

    @Override
    public void refreshUserInfoCache(User user) {
        userCacheManager.refreshUserInfoCache(user);
    }

    @Override
    public UserVO adminGetUser(Long userId) {
        return userQueryManager.adminGetUser(userId);
    }

    @Override
    public UserVO getCurrentUserVO() {
        return userQueryManager.getCurrentUserVO();
    }

}
