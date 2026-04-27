package hk.ljx.fishpicsbackend.service;

import hk.ljx.fishpicsbackend.dto.post.UploadPostRequest;
import hk.ljx.fishpicsbackend.entity.Post;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;

/**
* @author 30574
* @description 针对表【post(帖子表)】的数据库操作Service
* @createDate 2026-04-13 21:24:41
*/
public interface PostService extends IService<Post> {

    /**
     * 上传帖子
     *
     * @param uploadPostRequest 帖子内容
     */
    void uploadPost(UploadPostRequest uploadPostRequest, HttpServletRequest request);
}
