package hk.ljx.fishpicsbackend.service;

import hk.ljx.fishpicsbackend.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author 30574
* @description 针对表【picture(图片表)】的数据库操作Service
* @createDate 2026-04-13 21:24:49
*/
public interface PictureService extends IService<Picture> {

    String uploadAvatar(MultipartFile file, Long id, HttpServletRequest request);
}
