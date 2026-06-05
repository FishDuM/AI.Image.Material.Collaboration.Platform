package hk.ljx.fishpicsbackend.permission.service;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.mapper.RolePermissionMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.mapper.SysUserRoleMapper;
import hk.ljx.fishpicsbackend.permission.entity.SysUserRole;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限服务（新版）
 * 三层 RBAC：系统→团队→资源
 */
@Slf4j
@Service
public class PermissionService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final SpaceTeamMemberMapper spaceTeamMemberMapper;
    private final MultiLevelCacheManager cacheManager;

    /**
     * 角色名称映射
     */
    private static final Map<Integer, String> ROLE_NAME_MAP = Map.of(
            1, "系统超级管理员",
            2, "团队管理员",
            3, "普通成员",
            4, "只读成员"
    );

    public PermissionService(SysUserRoleMapper sysUserRoleMapper,
                             RolePermissionMapper rolePermissionMapper,
                             SpaceTeamMemberMapper spaceTeamMemberMapper,
                             MultiLevelCacheManager cacheManager) {
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.spaceTeamMemberMapper = spaceTeamMemberMapper;
        this.cacheManager = cacheManager;
    }

    /**
     * 根据角色ID获取权限列表
     */
    public List<String> getPermsByRoleId(Integer roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        return rolePermissionMapper.selectPermKeysByRoleId(roleId);
    }

    /**
     * 判断角色是否拥有指定权限
     */
    public boolean hasPermission(Integer roleId, String permKey) {
        if (roleId == null) {
            return false;
        }
        // 超管拥有所有权限
        if (Integer.valueOf(1).equals(roleId)) {
            return true;
        }
        List<String> perms = getPermsByRoleId(roleId);
        return perms.contains(permKey);
    }

    /**
     * 根据用户等级生成 VIP 权限列表
     */
    public static List<String> getVipPerms(Integer level) {
        if (level == null || level <= 0) {
            return Collections.emptyList();
        }
        List<String> perms = new ArrayList<>();
        // VIP (level>=1): upload:large + storage:expand
        perms.add("resource:upload:large");
        perms.add("resource:storage:expand");
        if (level >= 2) {
            // SVIP (level>=2): + ai:quota
            perms.add("resource:ai:quota");
        }
        return perms;
    }

    /**
     * 构建完整的权限上下文
     *
     * 优化说明：
     * - 系统角色查询 1 次
     * - 系统权限查询 1 次（如果角色存在）
     * - 团队成员查询 1 次（批量获取所有团队）
     * - 团队权限查询 N 次（每个团队一次，可进一步优化为批量）
     */
    public LoginContext buildLoginContext(Long userId, String username, String nickname,
                                          String avatar, Integer status, Integer level) {
        // 1. 查询系统角色
        SysUserRole sysUserRole = sysUserRoleMapper.selectOne(
                new QueryWrapper<SysUserRole>().eq("user_id", userId));
        Integer systemRoleId = sysUserRole != null ? sysUserRole.getRoleId() : null;

        // 2. 查询系统权限
        List<String> systemPerms = Collections.emptyList();
        if (systemRoleId != null) {
            systemPerms = getPermsByRoleId(systemRoleId);
        }

        // 3. 查询团队权限（批量查询，减少数据库交互）
        Map<Long, LoginContext.TeamPerm> teams = new HashMap<>();
        List<SpaceTeamMember> memberships = spaceTeamMemberMapper.selectList(
                new QueryWrapper<SpaceTeamMember>().eq("user_id", userId));

        if (!memberships.isEmpty()) {
            // 批量查询所有涉及的角色权限
            Set<Integer> roleIds = memberships.stream()
                    .map(SpaceTeamMember::getRoleId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<Integer, List<String>> rolePermsMap = new HashMap<>();
            for (Integer roleId : roleIds) {
                rolePermsMap.put(roleId, getPermsByRoleId(roleId));
            }

            // 组装团队权限
            for (SpaceTeamMember member : memberships) {
                Long spaceId = member.getSpaceId();
                Integer teamRoleId = member.getRoleId();
                if (teamRoleId == null) continue;

                List<String> teamPerms = rolePermsMap.getOrDefault(teamRoleId, Collections.emptyList());
                String roleName = ROLE_NAME_MAP.getOrDefault(teamRoleId, "未知角色");

                teams.put(spaceId, LoginContext.TeamPerm.builder()
                        .roleId(teamRoleId)
                        .roleName(roleName)
                        .perms(teamPerms)
                        .build());
            }
        }

        // 4. 生成 VIP 权限
        List<String> vipPerms = getVipPerms(level);

        // 5. 组装上下文
        return LoginContext.builder()
                .userId(userId)
                .username(username)
                .nickname(nickname)
                .avatar(avatar)
                .status(status)
                .level(level)
                .vipPerms(vipPerms)
                .systemRole(systemRoleId)
                .systemPerms(systemPerms)
                .teams(teams)
                .build();
    }

    /**
     * 获取用户的系统角色ID
     */
    public Integer getUserSystemRoleId(Long userId) {
        SysUserRole sysUserRole = sysUserRoleMapper.selectOne(
                new QueryWrapper<SysUserRole>().eq("user_id", userId));
        return sysUserRole != null ? sysUserRole.getRoleId() : null;
    }

    /**
     * 获取用户的系统角色ID列表（兼容旧代码）
     */
    public List<Long> getUserRoleIds(Long userId) {
        Integer roleId = getUserSystemRoleId(userId);
        if (roleId != null) {
            return List.of(roleId.longValue());
        }
        return Collections.emptyList();
    }

    /**
     * 为用户分配系统角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignUserRole(Long userId, Integer roleId) {
        // 先删除旧的
        sysUserRoleMapper.delete(
                new QueryWrapper<SysUserRole>().eq("user_id", userId));
        // 插入新的（只允许超管角色）
        if (Integer.valueOf(1).equals(roleId)) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
        clearUserPermissionCache(userId);
    }

    /**
     * 移除用户的系统角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeUserRole(Long userId) {
        sysUserRoleMapper.delete(
                new QueryWrapper<SysUserRole>().eq("user_id", userId));
        clearUserPermissionCache(userId);
    }

    /**
     * 将用户加入团队空间并分配角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void addTeamMember(Long spaceId, Long userId, Long roleId) {
        Integer intRoleId = roleId != null ? roleId.intValue() : null;
        SpaceTeamMember existing = spaceTeamMemberMapper.selectOne(
                new QueryWrapper<SpaceTeamMember>()
                        .eq("space_id", spaceId)
                        .eq("user_id", userId));
        if (existing != null) {
            existing.setRoleId(intRoleId);
            spaceTeamMemberMapper.updateById(existing);
        } else {
            SpaceTeamMember member = new SpaceTeamMember();
            member.setSpaceId(spaceId);
            member.setUserId(userId);
            member.setRoleId(intRoleId);
            spaceTeamMemberMapper.insert(member);
        }
        clearUserPermissionCache(userId);
    }

    /**
     * 从团队空间移除成员
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeTeamMember(Long spaceId, Long userId) {
        spaceTeamMemberMapper.delete(
                new QueryWrapper<SpaceTeamMember>()
                        .eq("space_id", spaceId)
                        .eq("user_id", userId));
        clearUserPermissionCache(userId);
    }

    /**
     * 获取团队空间的成员角色ID
     */
    public Integer getTeamMemberRole(Long userId, Long spaceId) {
        SpaceTeamMember member = spaceTeamMemberMapper.selectOne(
                new QueryWrapper<SpaceTeamMember>()
                        .eq("space_id", spaceId)
                        .eq("user_id", userId)
                        .select("role_id")
                        .last("LIMIT 1"));
        return member != null ? member.getRoleId() : null;
    }

    /**
     * 判断用户在团队空间中是否有指定权限
     */
    public boolean hasTeamPermission(Long userId, Long spaceId, String permKey) {
        // 超管拥有所有权限
        Integer systemRoleId = getUserSystemRoleId(userId);
        if (Integer.valueOf(1).equals(systemRoleId)) {
            return true;
        }
        // 查询团队角色
        Integer teamRoleId = getTeamMemberRole(userId, spaceId);
        if (teamRoleId == null) {
            return false;
        }
        return hasPermission(teamRoleId, permKey);
    }

    /**
     * 清除用户的权限缓存
     */
    public void clearUserPermissionCache(Long userId) {
        cacheManager.getUserPermCache().evict(String.valueOf(userId));
        log.info("清除用户权限缓存: userId={}", userId);
    }
}
