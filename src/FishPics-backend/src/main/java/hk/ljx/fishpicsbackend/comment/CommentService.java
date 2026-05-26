package hk.ljx.fishpicsbackend.comment;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import hk.ljx.fishpicsbackend.comment.dto.CommentQueryRequest;
import hk.ljx.fishpicsbackend.comment.dto.CreateCommentRequest;
import hk.ljx.fishpicsbackend.comment.vo.CommentVO;

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

    IPage<CommentVO> getAdminCommentPage(CommentQueryRequest req);
}
