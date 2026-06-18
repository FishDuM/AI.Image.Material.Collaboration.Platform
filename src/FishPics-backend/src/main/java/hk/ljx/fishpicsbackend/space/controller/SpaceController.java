package hk.ljx.fishpicsbackend.space.controller;

import hk.ljx.fishpicsbackend.common.response.Response;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.annotation.RequireLogin;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.space.dto.*;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.space.vo.SpaceMemberVO;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 空间控制器，提供空间管理的REST API
 */
@RestController
@RequestMapping("/space")
@Slf4j
public class SpaceController {

    @Resource
    private SpaceService spaceService;

    /**
     * 创建空间
     * 
     * @param createSpace 创建空间请求体
     * @return 创建成功返回true
     */
    @RequireLogin
    @AuditLog(module = "空间管理", operation = "创建空间")
    @PostMapping("/create")
    public Response<Boolean> createSpace(@Valid @RequestBody CreateSpaceRequest createSpace) {
        User user = LoginContextHelper.requireUser();
        return Response.ok(spaceService.createSpace(createSpace, user));
    }

    /**
     * 获取当前用户的空间列表
     *
     * @param type 空间类型（0-私人空间，1-团队空间）
     * @return 空间列表
     */
    @RequireLogin
    @GetMapping("/list")
    public Response<List<SpaceVO>> listSpace(@RequestParam("type") Integer type) {
        ExcUtils.throwIfTrue(type == null, ExceptionCode.PARAMETER_ERROR, "空间类型不能为空");
        return Response.ok(spaceService.listSpace(type));
    }

    /**
     * 获取单个空间详情
     *
     * @param id 空间ID
     * @return 空间详情
     */
    @RequireLogin
    @GetMapping("/getSpace")
    public Response<SpaceVO> getSpace(@RequestParam("id") Long id) {
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        return Response.ok(spaceService.getSpace(id));
    }

    /**
     * 更新空间信息
     *
     * @param updateSpace 更新请求体
     * @return 更新成功返回true
     */
    @RequireLogin
    @AuditLog(module = "空间管理", operation = "更新空间")
    @PostMapping("/update")
    public Response<Boolean> updateSpace(@Valid @RequestBody UpdateSpaceRequest updateSpace) {
        return Response.ok(spaceService.updateSpace(updateSpace));
    }

    /**
     * 获取空间图片列表（分页）
     *
     * @param spacePictureList 查询请求体
     * @return 图片分页结果
     */
    @RequireLogin
    @PostMapping("/pictureList")
    public Response<IPage<PictureVO>> pictureList(@Valid @RequestBody SpacePictureListRequest spacePictureList) {
        return Response.ok(spaceService.pictureList(spacePictureList));
    }

    @RequireAdmin
    @AuditLog(module = "空间管理", operation = "查询空间列表")
    @PostMapping("/admin/list")
    public Response<IPage<SpaceVO>> adminList(@Valid @RequestBody SpaceQueryWrapper wrapper) {
        return Response.ok(spaceService.adminList(wrapper));
    }

    @RequireAdmin
    @PostMapping("/admin/update")
    @AuditLog(module = "空间管理", operation = "更新空间")
    public Response<Boolean> adminUpdate(@Valid @RequestBody SpaceAdminUpdateRequest request) {
        return Response.ok(spaceService.adminUpdate(request));
    }

    @RequireAdmin
    @PostMapping("/admin/delete")
    @AuditLog(module = "空间管理", operation = "删除空间")
    public Response<Boolean> adminDelete(@Valid @RequestBody SpaceDeleteRequest request) {
        return Response.ok(spaceService.adminDelete(request.getId()));
    }

    @RequireAdmin
    @PostMapping("/admin/setStatus")
    @AuditLog(module = "空间管理", operation = "空间状态变更")
    public Response<Boolean> adminSetStatus(@Valid @RequestBody SpaceSetStatusRequest request) {
        return Response.ok(spaceService.adminSetStatus(request.getId(), request.getStatus()));
    }

    @RequireLogin
    @GetMapping("/team/members")
    public Response<List<SpaceMemberVO>> teamMemberList(@RequestParam("spaceId") Long spaceId) {
        ExcUtils.throwIfTrue(spaceId == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        return Response.ok(spaceService.teamMemberList(spaceId));
    }

    @RequireLogin
    @AuditLog(module = "团队管理", operation = "邀请成员")
    @PostMapping("/team/invite")
    public Response<Boolean> teamInvite(@Valid @RequestBody TeamInviteRequest request) {
        return Response.ok(spaceService.teamInvite(request));
    }

    @RequireLogin
    @AuditLog(module = "团队管理", operation = "移除成员")
    @PostMapping("/team/remove")
    public Response<Boolean> teamRemove(@Valid @RequestBody TeamRemoveRequest request) {
        return Response.ok(spaceService.teamRemove(request));
    }

    @RequireLogin
    @AuditLog(module = "团队管理", operation = "变更角色")
    @PostMapping("/team/changeRole")
    public Response<Boolean> teamChangeRole(@Valid @RequestBody TeamChangeRoleRequest request) {
        return Response.ok(spaceService.teamChangeRole(request));
    }

    /**
     * 获取当前用户可保存图片的空间列表（私人空间 + 有上传权限的团队空间）
     */
    @RequireLogin
    @GetMapping("/saveable")
    public Response<List<SpaceVO>> saveableSpaces() {
        return Response.ok(spaceService.saveableSpaces());
    }
}
