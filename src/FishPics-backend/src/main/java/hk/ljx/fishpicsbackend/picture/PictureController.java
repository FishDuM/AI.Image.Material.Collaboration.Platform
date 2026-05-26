package hk.ljx.fishpicsbackend.picture;

import cn.hutool.core.util.ObjUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdList;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.Picture;
import hk.ljx.fishpicsbackend.picture.PictureService;
import hk.ljx.fishpicsbackend.picture.vo.PictureAdminVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private PictureService pictureService;

    @PostMapping("/avatar")
    public Response<String> uploadAvatar(@RequestParam("file") MultipartFile file, @RequestParam("id") Long id) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 5, "文件大小不能超过5MB");
        ExcUtils.throwIfTrue(id == null, "用户id不能为空");
        return ResUtils.success(pictureService.uploadAvatar(file, id));
    }

    /**
     * 上传图片
     * 支持指定目标空间ID（targetSpaceId），未传入时默认上传至私人空间
     *
     * @param file          上传的图片文件
     * @param targetSpaceId 目标空间ID，为null时默认上传至私人空间
     * @return 图片基本信息(id/url)
     */
    @PostMapping("/upload")
    public Response<PictureListVO> uploadPicture(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetSpaceId", required = false) Long targetSpaceId) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        Picture picture = pictureService.uploadPicture(file, targetSpaceId);
        PictureListVO pictureListVO = new PictureListVO();
        pictureListVO.setId(picture.getId());
        pictureListVO.setUrl(picture.getUrl());
        return ResUtils.success(pictureListVO);
    }

    @GetMapping("/list")
    public Response<IPage<PictureListVO>> getPictureList(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        ExcUtils.throwIfTrue(current < 1, "页码不能小于1");
        ExcUtils.throwIfTrue(pageSize < 1 || pageSize > 100, "每页数量应在1-100之间");
        return ResUtils.success(pictureService.getPictureList(current, pageSize, 1));
    }

    @DeleteMapping("/delete")
    public Response<String> deletePicture(@RequestBody DeleteByIdList deleteByIdList) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(deleteByIdList), "id不能为空");
        return ResUtils.successOfMessage(pictureService.deletePicture(deleteByIdList));
    }

    @PutMapping("/update")
    public Response<Boolean> updatePicture(@RequestBody PictureUpdateRequest request) {
        pictureService.updatePicture(request);
        return ResUtils.success(true);
    }

    @AuthCheck(role = ADMIN)
    @GetMapping("/admin/list")
    public Response<IPage<PictureAdminVO>> getPictureListAdmin(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "status") int status) {
        ExcUtils.throwIfTrue(current < 1, "页码不能小于1");
        ExcUtils.throwIfTrue(pageSize < 1 || pageSize > 100, "每页数量应在1-100之间");
        return ResUtils.success(pictureService.getAdminPictureList(current, pageSize, status));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/review")
    public Response<Boolean> reviewPicture(
            @RequestParam("pictureId") Long pictureId,
            @RequestParam("status") Integer status,
            @RequestParam("selected") Integer selected) {
        pictureService.reviewPicture(pictureId, status, selected);
        return ResUtils.success(true);
    }
}
