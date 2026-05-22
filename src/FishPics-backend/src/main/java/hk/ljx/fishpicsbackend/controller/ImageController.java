package hk.ljx.fishpicsbackend.controller;

import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.picture.*;
import hk.ljx.fishpicsbackend.service.image.ImageProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.awt.image.BufferedImage;
import java.util.Base64;

@RestController
@RequestMapping("/image")
@Slf4j
public class ImageController {

    @Resource
    private ImageProcessingService imageProcessingService;

    @PostMapping("/compress")
    public Response<String> compress(@RequestBody ImageCompressRequest request) {
        ExcUtils.throwIfTrue(request.getPictureId() == null, "图片id不能为空");
        String newUrl = imageProcessingService.compressPicture(request);
        return ResUtils.success(newUrl);
    }

    @PostMapping("/thumbnail")
    public Response<String> generateThumbnail(@RequestBody ImageThumbnailRequest request) {
        ExcUtils.throwIfTrue(request.getPictureId() == null, "图片id不能为空");
        String newUrl = imageProcessingService.generateThumbnail(request);
        return ResUtils.success(newUrl);
    }

    @PostMapping("/rotate")
    public Response<String> rotate(@RequestBody ImageRotateRequest request) {
        ExcUtils.throwIfTrue(request.getPictureId() == null, "图片id不能为空");
        ExcUtils.throwIfTrue(request.getAngle() == null, "旋转角度不能为空");
        String newUrl = imageProcessingService.rotatePicture(request);
        return ResUtils.success(newUrl);
    }

    @PostMapping("/auto-orient")
    public Response<String> autoOrient(@RequestParam("pictureId") Long pictureId) {
        ExcUtils.throwIfTrue(pictureId == null, "图片id不能为空");
        String newUrl = imageProcessingService.autoOrientPicture(pictureId);
        return ResUtils.success(newUrl);
    }

    @PostMapping("/watermark")
    public Response<String> addWatermark(@RequestBody ImageWatermarkAdvancedRequest request) {
        ExcUtils.throwIfTrue(request.getPictureId() == null, "图片id不能为空");
        ExcUtils.throwIfTrue(
                (request.getText() == null || request.getText().isEmpty())
                        && (request.getImageUrl() == null || request.getImageUrl().isEmpty()),
                "水印文字或水印图片至少提供一个"
        );
        String newUrl = imageProcessingService.addWatermarkAdvanced(request);
        return ResUtils.success(newUrl);
    }

    @PostMapping("/convert")
    public Response<String> convertFormat(@RequestBody ImageFormatConvertRequest request) {
        ExcUtils.throwIfTrue(request.getPictureId() == null, "图片id不能为空");
        ExcUtils.throwIfTrue(request.getTargetFormat() == null || request.getTargetFormat().isEmpty(), "目标格式不能为空");
        String newUrl = imageProcessingService.convertFormat(request);
        return ResUtils.success(newUrl);
    }

    @PostMapping("/batch")
    public Response<ImageBatchResult> batchProcess(@RequestBody ImageBatchRequest request) {
        ExcUtils.throwIfTrue(request.getPictureIds() == null || request.getPictureIds().isEmpty(), "图片id列表不能为空");
        ImageBatchResult result = imageProcessingService.batchProcess(request);
        return ResUtils.success(result);
    }

    @PostMapping("/clean-metadata")
    public Response<String> cleanMetadata(@RequestParam("pictureId") Long pictureId) {
        ExcUtils.throwIfTrue(pictureId == null, "图片id不能为空");
        String newUrl = imageProcessingService.cleanMetadata(pictureId);
        return ResUtils.success(newUrl);
    }

    @PostMapping("/heic/convert")
    public Response<String> handleHeic(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetFormat", defaultValue = "jpg") String targetFormat) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        try {
            BufferedImage image = imageProcessingService.handleHeic(file.getBytes());
            ExcUtils.throwIfTrue(image == null, "无法解析HEIC文件");
            byte[] imageBytes = imageProcessingService.imageToBytes(image, targetFormat);
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);
            return ResUtils.success(base64Data);
        } catch (Exception e) {
            log.error("HEIC转换失败", e);
            return ResUtils.fail("HEIC转换失败：" + e.getMessage());
        }
    }
}