package hk.ljx.fishpicsbackend.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import hk.ljx.fishpicsbackend.dto.post.EditPostRequest;
import hk.ljx.fishpicsbackend.dto.post.GetPictureBySpaceRequest;
import hk.ljx.fishpicsbackend.dto.post.PostQueryRequest;
import hk.ljx.fishpicsbackend.dto.post.UploadPostRequest;
import hk.ljx.fishpicsbackend.service.PostService;
import hk.ljx.fishpicsbackend.vo.post.PostDetailVO;
import hk.ljx.fishpicsbackend.vo.post.PostListVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@RestController
@RequestMapping("/post")
@Slf4j
public class PostController {

    @Resource
    private PostService postService;

    @PostMapping("/post")
    public Response<Boolean> uploadPost(@RequestBody UploadPostRequest uploadPostRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(uploadPostRequest), "发送失败，内容不能为空");
        postService.uploadPost(uploadPostRequest);
        return ResUtils.success(true);
    }

    @GetMapping("/getPost")
    public Response<PostDetailVO> getPost(@RequestParam("id") Long id) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), "帖子不存在");
        return ResUtils.success(postService.getPost(id));
    }

    @PostMapping("/editPost")
    public Response<Boolean> editPost(@RequestBody EditPostRequest editPostRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(editPostRequest), "编辑帖子，内容不能为空");
        postService.editPost(editPostRequest);
        return ResUtils.success(true);
    }

    @PostMapping("/postList")
    public Response<IPage<PostListVO>> getPostList(@RequestBody PostQueryRequest postQueryRequest) {
        return ResUtils.success(postService.getPostList(postQueryRequest));
    }

    @PostMapping("/like")
    public Response<Boolean> like(@RequestParam("id") Long id) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), "帖子不存在");
        postService.likePost(id);
        return ResUtils.success(true);
    }

    @PostMapping("/collect")
    public Response<Boolean> collect(@RequestParam("id") Long id) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), "帖子不存在");
        boolean collected = postService.collectPost(id);
        return ResUtils.success(collected);
    }

    @PostMapping("/pictureList")
    public Response<Map<String, Object>> getPictureList(@RequestBody GetPictureBySpaceRequest getPictureBySpaceRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(getPictureBySpaceRequest), "获取图片列表，空间不能为空");
        return ResUtils.success(postService.getPictureList(getPictureBySpaceRequest));
    }

    /**
     * 获取本人发布的帖子列表（分页）
     */
    @PostMapping("/myPosts")
    public Response<IPage<PostListVO>> getMyPosts(@RequestBody PageRequest pageRequest) {
        return ResUtils.success(postService.getMyPosts(pageRequest));
    }

    /**
     * 获取本人收藏的帖子列表（分页）
     */
    @PostMapping("/myCollects")
    public Response<IPage<PostListVO>> getMyCollects(@RequestBody PageRequest pageRequest) {
        return ResUtils.success(postService.getMyCollects(pageRequest));
    }

    /**
     * 获取本人点赞的帖子列表（分页）
     */
    @PostMapping("/myLikes")
    public Response<IPage<PostListVO>> getMyLikes(@RequestBody PageRequest pageRequest) {
        return ResUtils.success(postService.getMyLikes(pageRequest));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/list")
    public Response<IPage<PostListVO>> adminList(@RequestBody PostQueryRequest req) {
        return ResUtils.success(postService.getAdminPostPage(req));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/review")
    public Response<Boolean> adminReview(@RequestParam("id") Long id, @RequestParam("status") Integer status) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), "帖子ID不能为空");
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(status), "审核状态不能为空");
        postService.reviewPost(id, status);
        return ResUtils.success(true);
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/delete")
    public Response<Boolean> adminDelete(@RequestParam("id") Long id) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), "帖子ID不能为空");
        postService.adminDeletePost(id);
        return ResUtils.success(true);
    }
}
