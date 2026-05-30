package hk.ljx.fishpicsbackend.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.user.entity.UserFans;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.user.vo.FollowUserVO;
import org.apache.ibatis.annotations.Param;

/**
* @author 30574
* @description 针对表【user_fans(用户粉丝表)】的数据库操作Mapper
* @createDate 2026-04-26 13:51:33
* @Entity hk.ljx.fishpicsbackend.entity.UserFans
*/
public interface UserFansMapper extends BaseMapper<UserFans> {

    IPage<FollowUserVO> selectFansPage(Page<?> page, @Param("userId") Long userId);

    IPage<FollowUserVO> selectFollowsPage(Page<?> page, @Param("userId") Long userId);
}




