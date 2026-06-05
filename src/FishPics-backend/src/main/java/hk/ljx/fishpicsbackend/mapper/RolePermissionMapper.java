package hk.ljx.fishpicsbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.permission.entity.RolePermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 根据角色ID查询权限标识列表
     */
    @Select("SELECT p.perm_key FROM role_permission rp " +
            "INNER JOIN permission p ON rp.perm_id = p.id " +
            "WHERE rp.role_id = #{roleId}")
    List<String> selectPermKeysByRoleId(@Param("roleId") Integer roleId);
}
