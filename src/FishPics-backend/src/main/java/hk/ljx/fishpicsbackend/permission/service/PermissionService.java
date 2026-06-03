package hk.ljx.fishpicsbackend.permission.service;

import java.util.List;
import java.util.Set;

/**
 * 权限服务接口
 */
public interface PermissionService {

    // 获取用户的所有系统级权限码
    Set<String> getUserPermissions(Long userId);

    // 判断用户是否拥有指定权限
    boolean hasPermission(Long userId, String permissionCode);

    // 判断用户是否拥有多个指定权限中的任意一个（OR 逻辑）
    boolean hasAnyPermission(Long userId, List<String> permissionCodes);

    // 判断用户是否拥有多个指定权限中的全部（AND 逻辑）
    boolean hasAllPermissions(Long userId, List<String> permissionCodes);

    // 为用户分配系统角色
    void assignUserRole(Long userId, Long roleId);

    // 移除用户的系统角色
    void removeUserRole(Long userId, Long roleId);

    // 获取用户的系统角色ID列表
    List<Long> getUserRoleIds(Long userId);

    // 获取用户所在团队空间的所有权限码
    Set<String> getTeamPermissions(Long userId, Long spaceId);

    // 判断用户在团队空间中是否有指定权限
    boolean hasTeamPermission(Long userId, Long spaceId, String permissionCode);

    // 将用户加入团队空间并分配角色
    void addTeamMember(Long spaceId, Long userId, Long roleId);

    // 从团队空间移除成员
    void removeTeamMember(Long spaceId, Long userId);

    // 获取团队空间的成员角色ID
    Long getTeamMemberRole(Long userId, Long spaceId);

    // 清除用户权限缓存
    void clearUserPermissionCache(Long userId);
}
