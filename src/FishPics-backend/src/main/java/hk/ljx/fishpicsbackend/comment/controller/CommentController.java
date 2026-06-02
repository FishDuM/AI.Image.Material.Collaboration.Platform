package hk.ljx.fishpicsbackend.comment.controller;
import hk.ljx.fishpicsbackend.comment.service.CommentService;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.comment.dto.CommentQueryRequest;
import hk.ljx.fishpicsbackend.comment.dto.CreateCommentRequest;
import hk.ljx.fishpicsbackend.comment.dto.ReviewCommentDTO;
import hk.ljx.fishpicsbackend.comment.vo.CommentVO;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.dto.IdRequest;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

@RestController
@RequestMapping("/comment")
@Slf4j
public class CommentController {

    @Resource
    private CommentService commentService;

    @PostMapping("/create")
    public Response<Long> createComment(@RequestBody CreateCommentRequest createCommentRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(createCommentRequest), "评论内容不能为空");
        return ResUtils.success(commentService.createComment(createCommentRequest));
    }

    @PostMapping("/list")
    public Response<IPage<CommentVO>> getCommentList(@RequestBody CommentQueryRequest commentQueryRequest) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(commentQueryRequest), "查询条件不能为空");
        return ResUtils.success(commentService.getCommentPage(commentQueryRequest));
    }

    @PostMapping("/delete")
    public Response<Boolean> deleteComment(@RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(request.getId()), "评论ID不能为空");
        commentService.deleteComment(request.getId());
        return ResUtils.success(true);
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/admin/list")
    public Response<IPage<CommentVO>> adminList(@RequestBody CommentQueryRequest req) {
        return ResUtils.success(commentService.getAdminCommentPage(req));
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/review")
    public Response<Boolean> reviewComment(@RequestBody ReviewCommentDTO dto) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(dto.getId()), "评论ID不能为空");
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(dto.getStatus()), "审核状态不能为空");
        commentService.reviewComment(dto.getId(), dto.getStatus());
        return ResUtils.success(true);
    }

    @AuthCheck(role = ADMIN)
    @PostMapping("/adminDelete")
    public Response<Boolean> adminDeleteComment(@RequestBody IdRequest request) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(request.getId()), "评论ID不能为空");
        commentService.adminDeleteComment(request.getId());
        return ResUtils.success(true);
    }
}
