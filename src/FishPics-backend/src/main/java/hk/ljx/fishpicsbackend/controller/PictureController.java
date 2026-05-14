package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.ObjUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.dto.picture.DeleteByIdList;
import hk.ljx.fishpicsbackend.dto.picture.PictureCropRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureScaleRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureWatermarkRequest;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.service.PictureService;
import hk.ljx.fishpicsbackend.vo.picture.PictureAdminVO;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private PictureService pictureService;

    @PostMapping("/avatar")
    public Response<String> uploadAvatar(@RequestParam("file") MultipartFile file, @RequestParam("id") Long id,
            HttpServletRequest request) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 5, "文件大小不能超过5MB");
        ExcUtils.throwIfTrue(id == null, "用户id不能为空");
        return ResUtils.success(pictureService.uploadAvatar(file, id, request));
    }

    /**
     * 上传图片
     * 支持指定目标空间ID（targetSpaceId），未传入时默认上传至私人空间
     *
     * @param file          上传的图片文件
     * @param targetSpaceId 目标空间ID，为null时默认上传至私人空间
     * @param request       HTTP请求
     * @return 图片基本信息(id/url)
     */
    @PostMapping("/upload")
    public Response<PictureListVO> uploadPicture(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetSpaceId", required = false) Long targetSpaceId,
            HttpServletRequest request) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        Picture picture = pictureService.uploadPicture(file, targetSpaceId, request);
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
    public Response<String> deletePicture(@RequestBody DeleteByIdList deleteByIdList, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(deleteByIdList), "id不能为空");
        return ResUtils.successOfMessage(pictureService.deletePicture(deleteByIdList, request));
    }

    @PutMapping("/update")
    public Response<Boolean> updatePicture(@RequestBody PictureUpdateRequest request) {
        pictureService.updatePicture(request);
        return ResUtils.success(true);
    }

    /**
     * 裁剪图片
     * 从COS下载原图，按前端传入的原始图像坐标先裁剪、再旋转（如有），
     * 最后重新上传至COS并更新数据库，返回新的图片URL
     *
     * @param request        裁剪请求，含图片id、裁剪区域坐标(x/y/width/height)、旋转角度、输出格式
     * @param servletRequest HTTP请求，用于获取登录用户信息进行权限校验
     * @return 裁剪后新图片的COS访问URL
     */
    @PostMapping("/crop")
    public Response<String> cropPicture(@RequestBody PictureCropRequest request, HttpServletRequest servletRequest) {
        ExcUtils.throwIfTrue(request.getPictureId() == null, "图片id不能为空");
        String newUrl = pictureService.cropPicture(request, servletRequest);
        return ResUtils.success(newUrl);
    }

    /**
     * 缩放图片
     * 支持按比例缩放或按目标宽度等比缩放，处理完成后重新上传至COS并更新数据库
     *
     * @param request        缩放请求，含图片id、缩放比例(scale)或目标宽度(targetWidth)、输出格式
     * @param servletRequest HTTP请求，用于获取登录用户信息进行权限校验
     * @return 缩放后新图片的COS访问URL
     */
    @PostMapping("/scale")
    public Response<String> scalePicture(@RequestBody PictureScaleRequest request, HttpServletRequest servletRequest) {
        ExcUtils.throwIfTrue(request.getPictureId() == null, "图片id不能为空");
        ExcUtils.throwIfTrue(request.getScale() == null && request.getTargetWidth() == null, "缩放比例或目标宽度不能同时为空");
        String newUrl = pictureService.scalePicture(request, servletRequest);
        return ResUtils.success(newUrl);
    }

    /**
     * 添加文字水印
     * 在图片中央叠加半透明白色文字水印，处理完成后重新上传至COS并更新数据库
     *
     * @param request        水印请求，含图片id、水印文字、输出格式
     * @param servletRequest HTTP请求，用于获取登录用户信息进行权限校验
     * @return 添加水印后新图片的COS访问URL
     */
    @PostMapping("/watermark")
    public Response<String> watermarkPicture(@RequestBody PictureWatermarkRequest request,
            HttpServletRequest servletRequest) {
        ExcUtils.throwIfTrue(request.getPictureId() == null, "图片id不能为空");
        ExcUtils.throwIfTrue(request.getText() == null || request.getText().isEmpty(), "水印文字不能为空");
        String newUrl = pictureService.watermarkPicture(request, servletRequest);
        return ResUtils.success(newUrl);
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
