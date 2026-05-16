package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.ObjectUtil;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.dto.space.SpacePictureList;
import hk.ljx.fishpicsbackend.dto.space.UpdateSpace;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.service.SpaceService;
import hk.ljx.fishpicsbackend.vo.picture.PicturePageVO;
import hk.ljx.fishpicsbackend.vo.space.SpaceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间控制器，提供空间管理的REST API
 */
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceService spaceService;

    /**
     * 创建空间
     * @param createSpace 创建空间请求体
     * @param request HTTP请求
     * @return 创建成功返回true
     */
    @PostMapping("/create")
    public Response<Boolean> createSpace(@RequestBody CreateSpace createSpace, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(createSpace), ExceptionCode.PARAMETER_ERROR, "创建空间参数不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        return ResUtils.success(spaceService.createSpace(createSpace, user));
    }

    /**
     * 获取当前用户的空间列表
     * @param type 空间类型（0-私人空间，1-团队空间）
     * @return 空间列表
     */
    @GetMapping("/list")
    public Response<List<SpaceVO>> listSpace(@RequestParam("type") Integer type) {
        ExcUtils.throwIfTrue(type == null , ExceptionCode.PARAMETER_ERROR, "空间类型不能为空");
        return ResUtils.success(spaceService.listSpace(type));
    }

    /**
     * 获取单个空间详情
     * @param id 空间ID
     * @return 空间详情
     */
    @GetMapping("/getSpace")
    public Response<SpaceVO> getSpace(@RequestParam("id") Long id) {
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        return ResUtils.success(spaceService.getSpace(id));
    }

    /**
     * 更新空间信息
     * @param updateSpace 更新请求体
     * @return 更新成功返回true
     */
    @PostMapping("/update")
    public Response<Boolean> updateSpace(@RequestBody UpdateSpace updateSpace) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(updateSpace), ExceptionCode.PARAMETER_ERROR, "更新空间参数不能为空");
        return ResUtils.success(spaceService.updateSpace(updateSpace));
    }

    /**
     * 获取空间图片列表（分页）
     * @param spacePictureList 查询请求体
     * @return 图片分页结果
     */
    @PostMapping("/pictureList")
    public Response<PicturePageVO> pictureList(@RequestBody SpacePictureList spacePictureList) {
        ExcUtils.throwIfTrue(spacePictureList == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        return ResUtils.success(spaceService.pictureList(spacePictureList));
    }
}
