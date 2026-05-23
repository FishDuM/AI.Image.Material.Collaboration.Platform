package hk.ljx.fishpicsbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.dto.comment.CommentQueryRequest;
import hk.ljx.fishpicsbackend.dto.comment.CreateCommentRequest;
import hk.ljx.fishpicsbackend.entity.Comment;
import hk.ljx.fishpicsbackend.entity.Post;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.CommentMapper;
import hk.ljx.fishpicsbackend.service.PostService;
import hk.ljx.fishpicsbackend.service.UserService;
import hk.ljx.fishpicsbackend.vo.comment.CommentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock private CommentMapper commentMapper;
    @Mock private UserService userService;
    @Mock private PostService postService;

    @InjectMocks
    private CommentServiceImpl commentService;

    private MockedStatic<UserHolder> userHolderMock;
    private User testUser;
    private Post testPost;

    @BeforeEach
    void setUp() {
        userHolderMock = mockStatic(UserHolder.class);
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setRole("user");

        testPost = Post.builder().id(10L).userId(2L).commentNum(5).build();

        ReflectionTestUtils.setField(commentService, "baseMapper", commentMapper);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    @Nested
    class CreateCommentTests {

        @Test
        void shouldCreateCommentSuccessfully() {
            CreateCommentRequest req = new CreateCommentRequest(10L, "好帖子!", null, null);
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postService.getById(10L)).thenReturn(testPost);
            doAnswer(inv -> { Comment c = inv.getArgument(0); c.setId(100L); return 1; }).when(commentMapper).insert(ArgumentMatchers.<Comment>any());
            when(postService.update(any(LambdaUpdateWrapper.class))).thenReturn(true);

            Long commentId = commentService.createComment(req);

            assertNotNull(commentId);
            verify(commentMapper).insert(ArgumentMatchers.<Comment>any());
            verify(postService).update(any(LambdaUpdateWrapper.class));
        }

        @Test
        void shouldRejectWhenPostIdIsNull() {
            CreateCommentRequest req = new CreateCommentRequest(null, "内容", null, null);
            assertThrows(BaseException.class, () -> commentService.createComment(req));
        }

        @Test
        void shouldRejectWhenContentIsNull() {
            CreateCommentRequest req = new CreateCommentRequest(10L, null, null, null);
            assertThrows(BaseException.class, () -> commentService.createComment(req));
        }

        @Test
        void shouldRejectWhenNotLoggedIn() {
            userHolderMock.when(UserHolder::getUser).thenReturn(null);
            CreateCommentRequest req = new CreateCommentRequest(10L, "内容", null, null);

            assertThrows(BaseException.class, () -> commentService.createComment(req));
        }

        @Test
        void shouldRejectWhenParentCommentNotFound() {
            CreateCommentRequest req = new CreateCommentRequest(10L, "回复", 99L, null);
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(commentMapper.selectById(99L)).thenReturn(null);

            assertThrows(BaseException.class, () -> commentService.createComment(req));
        }

        @Test
        void shouldRejectWhenParentCommentBelongsToDifferentPost() {
            Comment parent = new Comment();
            parent.setId(5L);
            parent.setPostId(20L);

            CreateCommentRequest req = new CreateCommentRequest(10L, "回复", 5L, null);
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(commentMapper.selectById(5L)).thenReturn(parent);

            assertThrows(BaseException.class, () -> commentService.createComment(req));
        }

        @Test
        void shouldSetStatusToPendingOnCreate() {
            CreateCommentRequest req = new CreateCommentRequest(10L, "评论", null, null);
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postService.getById(10L)).thenReturn(testPost);
            doAnswer(inv -> { Comment c = inv.getArgument(0); c.setId(100L); return 1; }).when(commentMapper).insert(ArgumentMatchers.<Comment>any());
            when(postService.update(any(LambdaUpdateWrapper.class))).thenReturn(true);

            commentService.createComment(req);

            ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
            verify(commentMapper).insert(captor.capture());
            assertEquals(2, captor.getValue().getStatus());
        }
    }

    @Nested
    class DeleteCommentTests {

        @Test
        void shouldDeleteOwnComment() {
            Comment comment = new Comment();
            comment.setId(1L);
            comment.setUserId(1L);
            comment.setPostId(10L);
            comment.setStatus(1);

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(commentMapper.selectById(1L)).thenReturn(comment);
            when(commentMapper.updateById(any(Comment.class))).thenReturn(1);
            when(postService.update(any(LambdaUpdateWrapper.class))).thenReturn(true);

            assertDoesNotThrow(() -> commentService.deleteComment(1L));
            assertEquals(0, comment.getStatus());
        }

        @Test
        void shouldRejectWhenCommentNotFound() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(commentMapper.selectById(99L)).thenReturn(null);

            assertThrows(BaseException.class, () -> commentService.deleteComment(99L));
        }

        @Test
        void shouldRejectWhenNotOwnComment() {
            Comment comment = new Comment();
            comment.setId(1L);
            comment.setUserId(999L);

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(commentMapper.selectById(1L)).thenReturn(comment);

            assertThrows(BaseException.class, () -> commentService.deleteComment(1L));
        }
    }

    @Nested
    class ReviewCommentTests {

        @Test
        void shouldApproveComment() {
            User admin = new User();
            admin.setId(99L);
            admin.setRole(ADMIN);

            Comment comment = new Comment();
            comment.setId(1L);
            comment.setPostId(10L);
            comment.setStatus(2);

            userHolderMock.when(UserHolder::getUser).thenReturn(admin);
            when(commentMapper.selectById(1L)).thenReturn(comment);
            when(commentMapper.updateById(any(Comment.class))).thenReturn(1);
            lenient().when(postService.update(any(LambdaUpdateWrapper.class))).thenReturn(true);

            commentService.reviewComment(1L, 1);

            assertEquals(1, comment.getStatus());
        }

        @Test
        void shouldRejectNonAdmin() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);

            assertThrows(BaseException.class, () -> commentService.reviewComment(1L, 1));
        }

        @Test
        void shouldRejectInvalidStatus() {
            User admin = new User();
            admin.setRole(ADMIN);
            userHolderMock.when(UserHolder::getUser).thenReturn(admin);

            assertThrows(BaseException.class, () -> commentService.reviewComment(1L, 2));
        }

        @Test
        void shouldRejectWhenCommentNotFound() {
            User admin = new User();
            admin.setRole(ADMIN);
            userHolderMock.when(UserHolder::getUser).thenReturn(admin);
            when(commentMapper.selectById(99L)).thenReturn(null);

            assertThrows(BaseException.class, () -> commentService.reviewComment(99L, 1));
        }
    }

    @Nested
    class AdminDeleteCommentTests {

        @Test
        void shouldDeleteCommentAndReplies() {
            User admin = new User();
            admin.setRole(ADMIN);

            Comment comment = new Comment();
            comment.setId(1L);
            comment.setPostId(10L);
            comment.setParentId(null);
            comment.setStatus(1);

            Comment reply = new Comment();
            reply.setId(2L);
            reply.setParentId(1L);
            reply.setStatus(1);

            userHolderMock.when(UserHolder::getUser).thenReturn(admin);
            when(commentMapper.selectById(1L)).thenReturn(comment);
            when(commentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(reply));
            when(commentMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
            when(commentMapper.deleteById(1L)).thenReturn(1);
            when(postService.update(any(LambdaUpdateWrapper.class))).thenReturn(true);

            assertDoesNotThrow(() -> commentService.adminDeleteComment(1L));

            verify(commentMapper).delete(any(LambdaQueryWrapper.class));
            verify(commentMapper).deleteById(1L);
        }

        @Test
        void shouldRejectNonAdmin() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);

            assertThrows(BaseException.class, () -> commentService.adminDeleteComment(1L));
        }

        @Test
        void shouldRejectWhenCommentNotFound() {
            User admin = new User();
            admin.setRole(ADMIN);
            userHolderMock.when(UserHolder::getUser).thenReturn(admin);
            when(commentMapper.selectById(99L)).thenReturn(null);

            assertThrows(BaseException.class, () -> commentService.adminDeleteComment(99L));
        }
    }

    @Nested
    class GetCommentPageTests {

        @Test
        void shouldReturnEmptyPageWhenNoComments() {
            CommentQueryRequest req = new CommentQueryRequest(10L, null);
            req.setCurrent(1);
            req.setPageSize(10);

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(commentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(new Page<Comment>(1, 10).setRecords(Collections.emptyList()));

            IPage<CommentVO> result = commentService.getCommentPage(req);

            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        void shouldRejectWhenPostIdIsNull() {
            CommentQueryRequest req = new CommentQueryRequest(null, null);
            assertThrows(BaseException.class, () -> commentService.getCommentPage(req));
        }
    }
}
