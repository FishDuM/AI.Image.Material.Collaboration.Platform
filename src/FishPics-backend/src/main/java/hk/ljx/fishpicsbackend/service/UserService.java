package hk.ljx.fishpicsbackend.service;

import hk.ljx.fishpicsbackend.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;

/**
* @author 30574
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2026-04-13 21:24:26
*/
public interface UserService extends IService<User> {

    /**
     * 系统内获取当前登录用户
     * @param request request
     * @return 用户实体
     */
    User getLoginUser(HttpServletRequest request);
}
