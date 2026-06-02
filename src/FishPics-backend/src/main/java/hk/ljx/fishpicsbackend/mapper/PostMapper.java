package hk.ljx.fishpicsbackend.mapper;

import hk.ljx.fishpicsbackend.post.entity.Post;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author 30574
* @description 针对表【post(帖子表)】的数据库操作Mapper
* @createDate 2026-04-26 14:46:30
* @Entity hk.ljx.fishpicsbackend.entity.Post
*/
public interface PostMapper extends BaseMapper<Post> {

    /**
     * 查询近30天有点赞或收藏行为的活跃用户ID
     */
    @Select("""
            SELECT DISTINCT user_id FROM (
                SELECT user_id FROM user_post_likes
                WHERE create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                UNION
                SELECT user_id FROM user_post_collect
                WHERE create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
            ) t
            """)
    List<Long> selectActiveUserIds();

}




