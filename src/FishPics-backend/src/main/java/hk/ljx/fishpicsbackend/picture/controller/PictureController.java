package hk.ljx.fishpicsbackend.picture.controller;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.picture.dto.AdminPictureListDTO;
import hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdList;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureQueryRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.dto.ReviewPictureDTO;
import hk.ljx.fishpicsbackend.picture.dto.SavePictureByUrlRequest;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
    public Response<String> uploadAvatar(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "id", required = false) Long targetUserId) {
        User currentUser = UserHolder.getUser();
        ExcUtils.throwIfTrue(currentUser == null, ExceptionCode.NOT_LOGIN);
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 5, "文件大小不能超过5MB");
        Long actualTargetUserId = targetUserId != null ? targetUserId : currentUser.getId();
        return ResUtils.success(pictureService.uploadAvatar(file, actualTargetUserId));
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
    @AuditLog(module = "图片管理", operation = "上传图片")
    public Response<PictureVO> uploadPicture(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetSpaceId", required = false) Long targetSpaceId) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        Picture picture = pictureService.uploadPicture(file, targetSpaceId);
        PictureVO pictureVO = PictureVO.ofUpload(picture.getId(), picture.getUrl());
        return ResUtils.success(pictureVO);
    }

    /**
     * 通过 URL 保存图片到空间
     */
    @PostMapping("/save-by-url")
    public Response<PictureVO> savePictureByUrl(@Valid @RequestBody SavePictureByUrlRequest request) {
        Picture picture = pictureService.savePictureByUrl(request.getUrl(), request.getTargetSpaceId());
        PictureVO pictureVO = PictureVO.ofUpload(picture.getId(), picture.getUrl());
        return ResUtils.success(pictureVO);
    }

    @PostMapping("/list")
    public Response<IPage<PictureVO>> getPictureList(@Valid @RequestBody PictureQueryRequest pictureQueryRequest) {
        return ResUtils.success(pictureService.getPictureList(pictureQueryRequest));
    }

    /**
     * 获取推荐图片列表（基于用户兴趣画像）
     */
    @PostMapping("/recommend")
    public Response<IPage<PictureVO>> getRecommendPictures(@Valid @RequestBody PageRequest pageRequest) {
        User loginUser = UserHolder.getUser();
        ExcUtils.throwIfTrue(loginUser == null, "请先登录");
        return ResUtils.success(pictureService.getRecommendPictures(pageRequest, loginUser.getId()));
    }

    @PostMapping("/delete")
    @AuditLog(module = "图片管理", operation = "删除图片")
    public Response<String> deletePicture(@Valid @RequestBody DeleteByIdList deleteByIdList) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(deleteByIdList), "id不能为空");
        return ResUtils.successOfMessage(pictureService.deletePicture(deleteByIdList));
    }

    @PutMapping("/update")
    public Response<Boolean> updatePicture(@Valid @RequestBody PictureUpdateRequest request) {
        pictureService.updatePicture(request);
        return ResUtils.success(true);
    }

    /**
     * 协同编辑：替换图片文件（前端发送变换后的图片 blob，后端上传 COS + 更新记录）
     */
    @PostMapping("/replace")
    public Response<Boolean> replacePictureFile(@RequestParam("file") MultipartFile file,
                                                @RequestParam("pictureId") Long pictureId) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        pictureService.replacePictureFile(pictureId, file);
        return ResUtils.success(true);
    }

    @GetMapping("/pictureEditMessage")
    public Response<PictureVO> getPictureEditMessage(@RequestParam Long id) {
        return ResUtils.success(pictureService.getPictureEditMessage(id));
    }

    @RequireAdmin
    @PostMapping("/admin/list")
    public Response<IPage<PictureVO>> getPictureListAdmin(@Valid @RequestBody AdminPictureListDTO dto) {
        return ResUtils.success(pictureService.getAdminPictureList(dto));
    }

    @RequireAdmin
    @PostMapping("/admin/review")
    @AuditLog(module = "图片管理", operation = "图片审核")
    public Response<Boolean> reviewPicture(@Valid @RequestBody ReviewPictureDTO dto) {
        pictureService.reviewPicture(dto.getPictureId(), dto.getStatus(), dto.getSelected());
        return ResUtils.success(true);
    }

    // ==================== 分片上传接口 ====================

    /**
     * 秒传校验
     * 检查文件是否已存在（MD5+size），支持秒传和断点续传
     */
    @PostMapping("/check")
    public Response<?> checkUpload(@Valid @RequestBody CheckUploadRequest request) {
        return ResUtils.success(pictureService.checkUpload(request));
    }

    /**
     * 分片上传
     */
    @PostMapping("/upload-chunk")
    public Response<?> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("md5") String md5,
            @RequestParam("chunkIndex") Integer chunkIndex) {
        return ResUtils.success(pictureService.uploadChunk(file, md5, chunkIndex));
    }

    /**
     * 合并分片
     */
    @PostMapping("/merge")
    public Response<PictureVO> mergeChunks(@Valid @RequestBody MergeChunksRequest request) {
        return ResUtils.success(pictureService.mergeChunks(request));
    }
}
