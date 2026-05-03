package hk.ljx.fishpicsbackend.controller;

import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.dto.picture.PictureMessage;
import hk.ljx.fishpicsbackend.service.PictureService;
import hk.ljx.fishpicsbackend.vo.picture.PictureAdminVO;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import hk.ljx.fishpicsbackend.vo.picture.PicturePostVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private PictureService pictureService;

    @PostMapping("/avatar")
    public Response<String> uploadAvatar(@RequestParam("file") MultipartFile file,@RequestParam Long id , HttpServletRequest request) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 5, "文件大小不能超过5MB");
        ExcUtils.throwIfTrue(id == null, "用户id不能为空");
        return ResUtils.success(pictureService.uploadAvatar(file,id,request));
    }

    @PostMapping("/post")
    public Response<PicturePostVO> uploadPicture4Post(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        ExcUtils.throwIfTrue(file.isEmpty(), "文件不能为空");
        ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 5, "文件大小不能超过5MB");
        return ResUtils.success(pictureService.uploadPicture4Post(file, request));
    }

    @GetMapping("/list")
    public Response<IPage<PictureListVO>> getPictureList(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        ExcUtils.throwIfTrue(current < 1, "页码不能小于1");
        ExcUtils.throwIfTrue(pageSize < 1 || pageSize > 100, "每页数量应在1-100之间");
        return ResUtils.success(pictureService.getPictureList(current, pageSize, 1));
    }

    @AuthCheck(role = ADMIN)
    @GetMapping("/admin/list")
    public Response<IPage<PictureAdminVO>> getPictureListAdmin(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        ExcUtils.throwIfTrue(current < 1, "页码不能小于1");
        ExcUtils.throwIfTrue(pageSize < 1 || pageSize > 100, "每页数量应在1-100之间");
        return ResUtils.success(pictureService.getAdminPictureList(current, pageSize));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/review")
    public Response<Boolean> reviewPicture(
            @RequestParam Long pictureId,
            @RequestParam Integer status) {
        pictureService.reviewPicture(pictureId, status);
        return ResUtils.success(true);
    }
}
