package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.post.EditPostRequest;
import hk.ljx.fishpicsbackend.dto.post.PostQueryRequest;
import hk.ljx.fishpicsbackend.dto.post.UploadPostRequest;
import hk.ljx.fishpicsbackend.service.PostService;
import hk.ljx.fishpicsbackend.vo.post.PostDetailVO;
import hk.ljx.fishpicsbackend.vo.post.PostListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/post")
@Slf4j
public class PostController {

    @Resource
    private PostService postService;

    @PostMapping("/post")
    public Response<Boolean> uploadPost(@RequestBody UploadPostRequest uploadPostRequest, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(uploadPostRequest), "发送失败，内容不能为空");
        postService.uploadPost(uploadPostRequest, request);
        return ResUtils.success(true);
    }

    @GetMapping("/getPost")
    public Response<PostDetailVO> getPost(@RequestParam("id") Long id) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), "帖子不存在");
        return ResUtils.success(postService.getPost(id));
    }

    @PostMapping("/editPost")
    public Response<Boolean> editPost(@RequestBody EditPostRequest editPostRequest, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(editPostRequest), "编辑帖子，内容不能为空");
        postService.editPost(editPostRequest, request);
        return ResUtils.success(true);
    }

    @PostMapping("/postList")
    public Response<IPage<PostListVO>> getPostList(@RequestBody PostQueryRequest postQueryRequest) {
        return ResUtils.success(postService.getPostList(postQueryRequest));
    }

    @PostMapping("/like")
    public Response<Boolean> like(@RequestParam("id") Long id, HttpServletRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), "帖子不存在");
        postService.likePost(id, request);
        return ResUtils.success(true);
    }
}
