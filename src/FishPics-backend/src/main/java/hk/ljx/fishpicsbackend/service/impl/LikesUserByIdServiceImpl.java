package hk.ljx.fishpicsbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.entity.userFollows;
import hk.ljx.fishpicsbackend.mapper.UserFollowsMapper;
import hk.ljx.fishpicsbackend.service.LikesUserByIdService;
import org.springframework.stereotype.Service;

/**
* @author 30574
* @description 针对表【likes_user_by_id(用户关注表)】的数据库操作Service实现
* @createDate 2026-04-26 13:51:45
*/
@Service
public class LikesUserByIdServiceImpl extends ServiceImpl<UserFollowsMapper, userFollows>
    implements LikesUserByIdService {

}




