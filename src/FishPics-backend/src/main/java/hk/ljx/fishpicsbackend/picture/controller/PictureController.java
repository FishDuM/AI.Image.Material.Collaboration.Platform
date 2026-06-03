package hk.ljx.fishpicsbackend.picture.controller;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.picture.dto.AdminPictureListDTO;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdList;
import hk.ljx.fishpicsbackend.picture.dto.PictureQueryRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.dto.ReviewPictureDTO;
import hk.ljx.fishpicsbackend.picture.dto.SavePictureByUrlRequest;
import hk.ljx.fishpicsbackend.picture.vo.PictureAdminVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureEditVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureListVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 通过 URL 保存图片到空间
     */
    @PostMapping("/save-by-url")
    public Response<PictureListVO> savePictureByUrl(@RequestBody SavePictureByUrlRequest request) {
        Picture picture = pictureService.savePictureByUrl(request.getUrl(), request.getTargetSpaceId());
        PictureListVO pictureListVO = new PictureListVO();
        pictureListVO.setId(picture.getId());
        pictureListVO.setUrl(picture.getUrl());
        return ResUtils.success(pictureListVO);
    }

    @PostMapping("/list")
    public Response<IPage<PictureListVO>> getPictureList(@RequestBody PictureQueryRequest pictureQueryRequest) {
        return ResUtils.success(pictureService.getPictureList(pictureQueryRequest));
    }

    /**
     * 获取推荐图片列表（基于用户兴趣画像）
     */
    @PostMapping("/recommend")
    public Response<IPage<PictureListVO>> getRecommendPictures(@RequestBody PageRequest pageRequest) {
        User loginUser = UserHolder.getUser();
        ExcUtils.throwIfTrue(loginUser == null, "请先登录");
        return ResUtils.success(pictureService.getRecommendPictures(pageRequest, loginUser.getId()));
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

    @GetMapping("/pictureEditMessage")
    public Response<PictureEditVO> getPictureEditMessage(Long id) {
        return ResUtils.success(pictureService.getPictureEditMessage(id));
    }

    @AuthCheck(permission = "picture:list")
    @PostMapping("/admin/list")
    public Response<IPage<PictureAdminVO>> getPictureListAdmin(@RequestBody AdminPictureListDTO dto) {
        return ResUtils.success(pictureService.getAdminPictureList(dto));
    }

    @AuthCheck(permission = "picture:review")
    @PostMapping("/admin/review")
    @AuditLog(module = "图片管理", operation = "图片审核")
    public Response<Boolean> reviewPicture(@RequestBody ReviewPictureDTO dto) {
        pictureService.reviewPicture(dto.getPictureId(), dto.getStatus(), dto.getSelected());
        return ResUtils.success(true);
    }
}
