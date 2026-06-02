package hk.ljx.fishpicsbackend.post.controller;
import hk.ljx.fishpicsbackend.post.service.PostService;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.dto.IdRequest;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.post.dto.EditPostRequest;
import hk.ljx.fishpicsbackend.post.dto.GetPictureBySpaceRequest;
import hk.ljx.fishpicsbackend.post.dto.PostQueryRequest;
import hk.ljx.fishpicsbackend.post.dto.ReviewPostDTO;
import hk.ljx.fishpicsbackend.post.dto.UploadPostRequest;
import hk.ljx.fishpicsbackend.post.vo.PictureListPageVO;
import hk.ljx.fishpicsbackend.post.vo.PostDetailVO;
import hk.ljx.fishpicsbackend.post.vo.PostListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

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
    public Response<Boolean> like(@RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(request.getId()), "帖子不存在");
        boolean liked = postService.likePost(request.getId());
        return ResUtils.success(liked);
    }

    @PostMapping("/collect")
    public Response<Boolean> collect(@RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(request.getId()), "帖子不存在");
        boolean collected = postService.collectPost(request.getId());
        return ResUtils.success(collected);
    }

    @PostMapping("/pictureList")
    public Response<PictureListPageVO> getPictureList(@RequestBody GetPictureBySpaceRequest getPictureBySpaceRequest) {
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

    /**
     * 获取推荐帖子列表（基于用户兴趣画像）
     */
    @PostMapping("/recommend")
    public Response<IPage<PostListVO>> getRecommendPosts(@RequestBody PageRequest pageRequest) {
        User loginUser = UserHolder.getUser();
        ExcUtils.throwIfTrue(loginUser == null, "请先登录");
        return ResUtils.success(postService.getRecommendPosts(pageRequest, loginUser.getId()));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/list")
    public Response<IPage<PostListVO>> adminList(@RequestBody PostQueryRequest req) {
        return ResUtils.success(postService.getAdminPostPage(req));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/review")
    public Response<Boolean> adminReview(@RequestBody ReviewPostDTO dto) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(dto.getId()), "帖子ID不能为空");
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(dto.getStatus()), "审核状态不能为空");
        postService.reviewPost(dto.getId(), dto.getStatus());
        return ResUtils.success(true);
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/delete")
    public Response<Boolean> adminDelete(@RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(request.getId()), "帖子ID不能为空");
        postService.adminDeletePost(request.getId());
        return ResUtils.success(true);
    }
}
