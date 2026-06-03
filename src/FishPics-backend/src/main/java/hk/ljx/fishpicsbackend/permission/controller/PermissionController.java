package hk.ljx.fishpicsbackend.permission.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.mapper.SysRoleMapper;
import hk.ljx.fishpicsbackend.permission.entity.SysRole;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/permission")
public class PermissionController {

    @Resource
    private SysRoleMapper sysRoleMapper;

    // 获取系统角色列表（仅系统级角色）
    @AuthCheck(permission = "user:manage")
    @GetMapping("/roles")
    public Response<List<Map<String, Object>>> getRoles() {
        List<SysRole> roles = sysRoleMapper.selectList(
                new QueryWrapper<SysRole>()
                        .eq("is_delete", 0)
                        .eq("scope", 0)
                        .orderByAsc("id")
        );
        List<Map<String, Object>> roleList = roles.stream()
                .map(role -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", role.getId());
                    map.put("name", role.getName());
                    map.put("code", role.getCode());
                    return map;
                })
                .collect(Collectors.toList());
        return ResUtils.success(roleList);
    }
}
