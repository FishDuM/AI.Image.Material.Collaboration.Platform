package hk.ljx.fishpicsbackend.permission.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.mapper.*;
import hk.ljx.fishpicsbackend.permission.entity.SysPermission;
import hk.ljx.fishpicsbackend.permission.entity.SysRolePermission;
import hk.ljx.fishpicsbackend.permission.entity.SysUserRole;
import hk.ljx.fishpicsbackend.permission.service.PermissionService;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 */
@Service
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysRolePermissionMapper sysRolePermissionMapper;

    @Resource
    private SysPermissionMapper sysPermissionMapper;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MultiLevelCacheManager cacheManager;

    // ========== 系统级权限 ==========

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getUserPermissions(Long userId) {
        String key = String.valueOf(userId);

        // 多级缓存拿权限码，从缓存取出来的是JSONArray要转成Set
        Object cached = cacheManager.getUserPermCache().get(key);
        if (cached instanceof Collection) {
            return Set.copyOf((Collection<String>) cached);
        }

        // 缓存miss，查数据库
        Set<String> permissions = queryPermissionsFromDB(userId);

        // 写入多级缓存
        cacheManager.getUserPermCache().put(key, new HashSet<>(permissions));

        return permissions;
    }

    // 从数据库查询用户权限
    private Set<String> queryPermissionsFromDB(Long userId) {
        // 1. 查询用户的所有系统角色
        List<Long> roleIds = getUserRoleIds(userId);
        if (CollUtil.isEmpty(roleIds)) {
            return Collections.emptySet();
        }

        // 2. 处理角色继承：如果角色有 inheritRoleId，也纳入
        Set<Long> allRoleIds = roleIds.stream()
                .flatMap(roleId -> {
                    // 递归收集继承的角色ID
                    Set<Long> chain = collectInheritedRoles(roleId);
                    chain.add(roleId);
                    return chain.stream();
                })
                .collect(Collectors.toSet());

        // 3. 查询所有角色关联的权限码
        if (CollUtil.isEmpty(allRoleIds)) {
            return Collections.emptySet();
        }
        // 先查关联表获取权限ID，再用参数化查询避免SQL注入
        List<Long> permissionIds = sysRolePermissionMapper.selectList(
                new QueryWrapper<SysRolePermission>()
                        .in("role_id", allRoleIds)
                        .select("permission_id")
        ).stream().map(SysRolePermission::getPermissionId).collect(Collectors.toList());
        if (CollUtil.isEmpty(permissionIds)) {
            return Collections.emptySet();
        }
        return sysPermissionMapper.selectList(
                new QueryWrapper<SysPermission>()
                        .in("id", permissionIds)
                        .eq("is_delete", 0)
                        .eq("scope", 0) // 只返回系统级权限
                        .select("code")
        ).stream().map(p -> p.getCode()).collect(Collectors.toSet());
    }

    @Override
    // 清除用户权限缓存（同时清L1和L2）
    public void clearUserPermissionCache(Long userId) {
        cacheManager.getUserPermCache().evict(String.valueOf(userId));
    }

    // 递归收集角色继承链上的角色ID
    private Set<Long> collectInheritedRoles(Long roleId) {
        Set<Long> inherited = new java.util.HashSet<>();
        Long currentId = roleId;
        // 最多查 5 层防止死循环
        for (int i = 0; i < 5; i++) {
            var role = sysRoleMapper.selectById(currentId);
            if (role == null || role.getInheritRoleId() == null) {
                break;
            }
            if (!inherited.add(role.getInheritRoleId())) {
                break; // 防止循环继承
            }
            currentId = role.getInheritRoleId();
        }
        return inherited;
    }

    @Override
    public boolean hasPermission(Long userId, String permissionCode) {
        Set<String> permissions = getUserPermissions(userId);
        return permissions.contains(permissionCode);
    }

    @Override
    public boolean hasAnyPermission(Long userId, List<String> permissionCodes) {
        if (CollUtil.isEmpty(permissionCodes)) {
            return true;
        }
        Set<String> permissions = getUserPermissions(userId);
        return permissionCodes.stream().anyMatch(permissions::contains);
    }

    @Override
    public boolean hasAllPermissions(Long userId, List<String> permissionCodes) {
        if (CollUtil.isEmpty(permissionCodes)) {
            return true;
        }
        Set<String> permissions = getUserPermissions(userId);
        return permissions.containsAll(permissionCodes);
    }

    // ========== 用户角色管理 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserRole(Long userId, Long roleId) {
        // 检查是否已存在
        Long count = sysUserRoleMapper.selectCount(
                new QueryWrapper<SysUserRole>()
                        .eq("user_id", userId)
                        .eq("role_id", roleId));
        if (count == 0) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
        // 清除用户权限缓存
        clearUserPermissionCache(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUserRole(Long userId, Long roleId) {
        sysUserRoleMapper.delete(
                new QueryWrapper<SysUserRole>()
                        .eq("user_id", userId)
                        .eq("role_id", roleId));
        // 清除用户权限缓存
        clearUserPermissionCache(userId);
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        return sysUserRoleMapper.selectList(
                new QueryWrapper<SysUserRole>()
                        .eq("user_id", userId)
                        .select("role_id")
        ).stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    // ========== 团队空间权限 ==========

    @Override
    public Set<String> getTeamPermissions(Long userId, Long spaceId) {
        Long roleId = getTeamMemberRole(userId, spaceId);
        if (roleId == null) {
            return Collections.emptySet();
        }
        // 先查关联表获取权限ID，再用参数化查询避免SQL注入
        List<Long> permissionIds = sysRolePermissionMapper.selectList(
                new QueryWrapper<SysRolePermission>()
                        .eq("role_id", roleId)
                        .select("permission_id")
        ).stream().map(SysRolePermission::getPermissionId).collect(Collectors.toList());
        if (CollUtil.isEmpty(permissionIds)) {
            return Collections.emptySet();
        }
        return sysPermissionMapper.selectList(
                new QueryWrapper<SysPermission>()
                        .in("id", permissionIds)
                        .eq("is_delete", 0)
                        .eq("scope", 1) // 团队级权限
                        .select("code")
        ).stream().map(p -> p.getCode()).collect(Collectors.toSet());
    }

    @Override
    public boolean hasTeamPermission(Long userId, Long spaceId, String permissionCode) {
        Set<String> permissions = getTeamPermissions(userId, spaceId);
        return permissions.contains(permissionCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTeamMember(Long spaceId, Long userId, Long roleId) {
        // 检查是否已在团队中
        Long count = spaceTeamMemberMapper.selectCount(
                new QueryWrapper<SpaceTeamMember>()
                        .eq("space_id", spaceId)
                        .eq("user_id", userId));
        if (count > 0) {
            // 更新角色
            SpaceTeamMember member = spaceTeamMemberMapper.selectOne(
                    new QueryWrapper<SpaceTeamMember>()
                            .eq("space_id", spaceId)
                            .eq("user_id", userId));
            member.setRoleId(roleId);
            spaceTeamMemberMapper.updateById(member);
        } else {
            SpaceTeamMember member = new SpaceTeamMember();
            member.setSpaceId(spaceId);
            member.setUserId(userId);
            member.setRoleId(roleId);
            spaceTeamMemberMapper.insert(member);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTeamMember(Long spaceId, Long userId) {
        spaceTeamMemberMapper.delete(
                new QueryWrapper<SpaceTeamMember>()
                        .eq("space_id", spaceId)
                        .eq("user_id", userId));
    }

    @Override
    public Long getTeamMemberRole(Long userId, Long spaceId) {
        SpaceTeamMember member = spaceTeamMemberMapper.selectOne(
                new QueryWrapper<SpaceTeamMember>()
                        .eq("space_id", spaceId)
                        .eq("user_id", userId)
                        .select("role_id")
                        .last("LIMIT 1"));
        return member != null ? member.getRoleId() : null;
    }
}
