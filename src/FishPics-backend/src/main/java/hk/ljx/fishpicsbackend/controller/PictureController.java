package hk.ljx.fishpicsbackend.controller;

import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.service.CosService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private CosService cosService;

    @PostMapping("/upload")
    public Response<String> uploadPicture(@RequestParam("file") MultipartFile file) {
        log.info("开始上传图片: {}", file.getOriginalFilename());
        String key = cosService.uploadPicture(file);
        log.info("上传成功, key: {}", key);
        return ResUtils.success(key);
    }

    @GetMapping("/url")
    public Response<String> getImageUrl(@RequestParam("key") String key) {
        log.info("获取图片URL, key: {}", key);
        String url = cosService.getImageUrl(key);
        return ResUtils.success(url);
    }

    @PostMapping("/uploadAndGetUrl")
    public Response<Map<String, String>> uploadAndGetUrl(@RequestParam("file") MultipartFile file) {
        log.info("上传图片并获取URL: {}", file.getOriginalFilename());
        String key = cosService.uploadPicture(file);
        String url = cosService.getImageUrl(key);
        
        Map<String, String> result = new HashMap<>();
        result.put("key", key);
        result.put("url", url);
        
        log.info("上传成功并获取URL: {}", url);
        return ResUtils.success(result);
    }

    @GetMapping("/test/url")
    public Response<String> testGetUrl(@RequestParam("key") String key) {
        log.info("测试获取图片URL, key: {}", key);
        try {
            String url = cosService.getImageUrl(key);
            return ResUtils.success("URL: " + url);
        } catch (Exception e) {
            log.error("获取URL失败", e);
            return ResUtils.fail("获取URL失败: " + e.getMessage());
        }
    }

    @PostMapping("/test/upload")
    public Response<String> testUpload(@RequestParam("file") MultipartFile file) {
        log.info("测试上传图片: {}", file.getOriginalFilename());
        try {
            String key = cosService.uploadPicture(file);
            return ResUtils.success("上传成功, key: " + key);
        } catch (Exception e) {
            log.error("上传失败", e);
            return ResUtils.fail("上传失败: " + e.getMessage());
        }
    }
}
