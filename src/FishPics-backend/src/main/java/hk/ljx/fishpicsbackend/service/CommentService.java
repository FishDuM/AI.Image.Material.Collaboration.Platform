package hk.ljx.fishpicsbackend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.dto.comment.CommentQueryRequest;
import hk.ljx.fishpicsbackend.dto.comment.CreateCommentRequest;
import hk.ljx.fishpicsbackend.entity.Comment;
import hk.ljx.fishpicsbackend.vo.comment.CommentVO;

/**
* @author 30574
* @description 针对表【comment(评论表)】的数据库操作Service
* @createDate 2026-04-13 21:24:56
*/
public interface CommentService extends IService<Comment> {

    Long createComment(CreateCommentRequest req);

    IPage<CommentVO> getCommentPage(CommentQueryRequest req);

    void deleteComment(Long commentId);

    void reviewComment(Long commentId, Integer status);

    void adminDeleteComment(Long commentId);
}
