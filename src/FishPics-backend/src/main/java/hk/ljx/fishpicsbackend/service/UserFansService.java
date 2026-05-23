package hk.ljx.fishpicsbackend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.entity.UserFans;
import hk.ljx.fishpicsbackend.vo.user.FollowUserVO;

/**
* @author 30574
* @description 针对表【user_fans(用户粉丝表)】的数据库操作Service
* @createDate 2026-04-26 13:51:33
*/
public interface UserFansService extends IService<UserFans> {

    boolean follow(Long targetUserId);

    IPage<FollowUserVO> getFans(Long userId, int current, int pageSize);

    IPage<FollowUserVO> getFollows(Long userId, int current, int pageSize);
}
