package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.ObjectUtil;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.dto.space.SpacePictureList;
import hk.ljx.fishpicsbackend.dto.space.UpdateSpace;
import hk.ljx.fishpicsbackend.entity.Space;
import hk.ljx.fishpicsbackend.service.SpaceService;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceService spaceService;

    @PostMapping("/create")
    public Response<Boolean> createSpace(@RequestBody CreateSpace createSpace, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(createSpace), ExceptionCode.PARAMETER_ERROR, "创建空间参数不能为空");
        return ResUtils.success(spaceService.createSpace(createSpace, request));
    }

    @GetMapping("/list")
    public Response<List<Space>> listSpace(@RequestParam Integer type,HttpServletRequest request) {
        ExcUtils.throwIfTrue(type == null , ExceptionCode.PARAMETER_ERROR, "空间类型不能为空");
        return ResUtils.success(spaceService.listSpace(type, request));
    }

    @PostMapping("/update")
    public Response<Boolean> updateSpace(@RequestBody UpdateSpace updateSpace, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(updateSpace), ExceptionCode.PARAMETER_ERROR, "更新空间参数不能为空");
        return ResUtils.success(spaceService.updateSpace(updateSpace, request));
    }

    @PostMapping("/pictureList")
    public Response<List<PictureListVO>> pictureList(@RequestBody SpacePictureList spacePictureList, HttpServletRequest request) {
        ExcUtils.throwIfTrue(spacePictureList == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        return ResUtils.success(spaceService.pictureList(spacePictureList, request));
    }
}
