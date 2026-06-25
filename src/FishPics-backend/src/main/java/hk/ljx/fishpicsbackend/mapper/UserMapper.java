package hk.ljx.fishpicsbackend.mapper;

import hk.ljx.fishpicsbackend.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface UserMapper extends BaseMapper<User> {

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


