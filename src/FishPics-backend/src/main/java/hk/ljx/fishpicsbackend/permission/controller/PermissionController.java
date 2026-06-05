package hk.ljx.fishpicsbackend.permission.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.common.annotation.RequirePerm;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.mapper.RoleMapper;
import hk.ljx.fishpicsbackend.permission.entity.Role;
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
    private RoleMapper roleMapper;

    /**
     * 获取角色列表
     */
    @RequirePerm("system:user:manage")
    @GetMapping("/roles")
    public Response<List<Map<String, Object>>> getRoles() {
        List<Role> roles = roleMapper.selectList(
                new QueryWrapper<Role>().orderByAsc("id")
        );
        List<Map<String, Object>> roleList = roles.stream()
                .map(role -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", role.getId());
                    map.put("name", role.getRoleName());
                    map.put("description", role.getDescription());
                    return map;
                })
                .collect(Collectors.toList());
        return ResUtils.success(roleList);
    }
}
