package hk.ljx.fishpicsbackend.mapper;

import hk.ljx.fishpicsbackend.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
* @author 30574
* @description 针对表【user(用户表)】的数据库操作Mapper
* @createDate 2026-04-26 14:45:38
* @Entity hk.ljx.fishpicsbackend.user.entity.User
*/
public interface UserMapper extends BaseMapper<User> {

    /**
     * 最后一名 admin 保护(并发安全版)。
     * 条件:目标用户降级后,系统中至少还有 1 个 role = 1 且 status = 1 的 admin。
     */
    @Update("UPDATE user SET role = 0 " +
            "WHERE id = #{userId} " +
            "AND role = 1 " +
            "AND (" +
            "  SELECT COUNT(*) FROM (" +
            "    SELECT 1 FROM user WHERE role = 1 AND status = 1 AND id != #{userId}" +
            "  ) AS remaining_admins" +
            ") >= 1")
    int updateRoleIfNotLastAdmin(@Param("userId") Long userId);
}




