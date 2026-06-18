package hk.ljx.fishpicsbackend.picture.controller;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.ai.service.AiService;

import hk.ljx.fishpicsbackend.common.response.Response;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.annotation.RequireLogin;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.picture.dto.AdminPictureListDTO;
import hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdListRequest;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureQueryRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.dto.ReviewPictureDTO;
import hk.ljx.fishpicsbackend.picture.dto.SavePictureByUrlRequest;
import hk.ljx.fishpicsbackend.picture.vo.CheckUploadVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.picture.vo.UploadChunkVO;
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

    @Resource
    private AiService aiService;

    private static final long MAX_UPLOAD_SIZE = 100L * 1024 * 1024;
    private static final String MD5_PATTERN = "^[a-fA-F0-9]{32}$";

    @RequireLogin
    @AuditLog(module = "用户管理", operation = "修改头像")
    @PostMapping("/avatar")
    public Response<String> uploadAvatar(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "id", required = false) Long targetUserId) {
        User currentUser = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 5, "文件大小不能超过5MB");
        Long actualTargetUserId = targetUserId != null ? targetUserId : currentUser.getId();
        return Response.ok(pictureService.uploadAvatar(file, actualTargetUserId));
    }

    /**
     * 上传图片
     * 支持指定目标空间ID（targetSpaceId），未传入时默认上传至私人空间
     *
     * @param file          上传的图片文件
     * @param targetSpaceId 目标空间ID，为null时默认上传至私人空间
     * @return 图片基本信息(id/url)
     */
    @RequireLogin
    @AuditLog(module = "图片管理", operation = "上传图片")
    @PostMapping("/upload")
    public Response<PictureVO> uploadPicture(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetSpaceId", required = false) Long targetSpaceId) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        ExcUtils.throwIfTrue(file.getSize() > MAX_UPLOAD_SIZE, ExceptionCode.PARAMETER_ERROR, "文件大小不能超过100MB");
        Picture picture = pictureService.uploadPicture(file, targetSpaceId);
        PictureVO pictureVO = PictureVO.ofUpload(picture.getId(), picture.getUrl());
        return Response.ok(pictureVO);
    }

    /**
     * 通过 URL 保存图片到空间
     */
    @RequireLogin
    @PostMapping("/save-by-url")
    public Response<PictureVO> savePictureByUrl(@Valid @RequestBody SavePictureByUrlRequest request) {
        Picture picture = pictureService.savePictureByUrl(request.getUrl(), request.getTargetSpaceId());
        PictureVO pictureVO = PictureVO.ofUpload(picture.getId(), picture.getUrl());
        return Response.ok(pictureVO);
    }

    @PostMapping("/list")
    public Response<IPage<PictureVO>> getPictureList(@Valid @RequestBody PictureQueryRequest pictureQueryRequest) {
        return Response.ok(pictureService.getPictureList(pictureQueryRequest));
    }

    @RequireLogin
    @PostMapping("/recommend")
    public Response<IPage<PictureVO>> getRecommendPictures(@Valid @RequestBody PageRequest pageRequest) {
        User loginUser = LoginContextHelper.requireUser();
        if (!aiService.isFeatureEnabled("recommendationEnabled")) {
            // 开关关闭：返回空分页
            return Response.ok(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                    pageRequest.getCurrent() <= 0 ? 1 : pageRequest.getCurrent(),
                    pageRequest.getPageSize() <= 0 ? 10 : pageRequest.getPageSize()));
        }
        return Response.ok(pictureService.getRecommendPictures(pageRequest, loginUser.getId()));
    }

    @RequireLogin
    @AuditLog(module = "图片管理", operation = "删除图片")
    @PostMapping("/delete")
    public Response<String> deletePicture(@Valid @RequestBody DeleteByIdListRequest deleteByIdList) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(deleteByIdList), "id不能为空");
        return Response.okMsg(pictureService.deletePicture(deleteByIdList));
    }

    @RequireLogin
    @AuditLog(module = "图片管理", operation = "编辑图片信息")
    @PutMapping("/update")
    public Response<Boolean> updatePicture(@Valid @RequestBody PictureUpdateRequest request) {
        pictureService.updatePicture(request);
        return Response.ok(true);
    }

    /**
     * 协同编辑：替换图片文件（前端发送变换后的图片 blob，后端上传 COS + 更新记录）
     */
    @RequireLogin
    @PostMapping("/replace")
    public Response<PictureVO> replacePictureFile(@RequestParam("file") MultipartFile file,
                                                @RequestParam("pictureId") Long pictureId,
                                                @RequestParam(value = "collab", defaultValue = "false") Boolean collab) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        PictureVO result = pictureService.replacePictureFile(pictureId, file, Boolean.TRUE.equals(collab));
        return Response.ok(result);
    }

    @RequireLogin
    @GetMapping("/pictureEditMessage")
    public Response<PictureVO> getPictureEditMessage(@RequestParam Long id) {
        return Response.ok(pictureService.getPictureEditMessage(id));
    }

    @RequireAdmin
    @PostMapping("/admin/list")
    public Response<IPage<PictureVO>> getPictureListAdmin(@Valid @RequestBody AdminPictureListDTO dto) {
        return Response.ok(pictureService.getAdminPictureList(dto));
    }

    @RequireAdmin
    @AuditLog(module = "图片管理", operation = "图片审核")
    @PostMapping("/admin/review")
    public Response<Boolean> reviewPicture(@Valid @RequestBody ReviewPictureDTO dto) {
        pictureService.reviewPicture(dto.getPictureId(), dto.getStatus(), dto.getSelected());
        return Response.ok(true);
    }

    // ==================== 分片上传接口 ====================

    /**
     * 秒传校验
     * 检查文件是否已存在（MD5+size），支持秒传和断点续传
     */
    @RequireLogin
    @PostMapping("/check")
    public Response<CheckUploadVO> checkUpload(@Valid @RequestBody CheckUploadRequest request) {
        return Response.ok(pictureService.checkUpload(request));
    }

    /**
     * 分片上传
     */
    @RequireLogin
    @PostMapping("/upload-chunk")
    public Response<UploadChunkVO> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("md5") String md5,
            @RequestParam("chunkIndex") Integer chunkIndex) {
        ExcUtils.throwIfTrue(md5 == null || !md5.matches(MD5_PATTERN), ExceptionCode.PARAMETER_ERROR, "MD5格式不正确");
        return Response.ok(pictureService.uploadChunk(file, md5, chunkIndex));
    }

    /**
     * 合并分片
     */
    @RequireLogin
    @PostMapping("/merge")
    public Response<PictureVO> mergeChunks(@Valid @RequestBody MergeChunksRequest request) {
        return Response.ok(pictureService.mergeChunks(request));
    }
}
