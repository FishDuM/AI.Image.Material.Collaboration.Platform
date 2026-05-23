package hk.ljx.fishpicsbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.dto.post.EditPostRequest;
import hk.ljx.fishpicsbackend.dto.post.UploadPostRequest;
import hk.ljx.fishpicsbackend.entity.*;
import hk.ljx.fishpicsbackend.mapper.PostMapper;
import hk.ljx.fishpicsbackend.service.*;
import hk.ljx.fishpicsbackend.vo.post.PostDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentMatchers;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock private PostMapper postMapper;
    @Mock private PictureChildService pictureChildService;
    @Mock private PictureService pictureService;
    @Mock private UserService userService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private UserPostLikesService userPostLikesService;
    @Mock private UserPostCollectService userPostCollectService;
    @Mock private SpaceService spaceService;

    @InjectMocks
    private PostServiceImpl postService;

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

        testPost = Post.builder()
                .id(10L).userId(2L).title("测试帖子").content("内容")
                .cover(100L).likesNum(5L).collectsNum(3L).status(1)
                .createTime(new Date()).build();

        ReflectionTestUtils.setField(postService, "baseMapper", postMapper);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    @Nested
    class UploadPostTests {

        @Test
        void shouldRejectWhenImagesEmpty() {
            UploadPostRequest req = new UploadPostRequest();
            req.setImageId(Collections.emptyList());
            assertThrows(BaseException.class, () -> postService.uploadPost(req));
        }

        @Test
        void shouldRejectWhenMoreThan15Images() {
            UploadPostRequest req = new UploadPostRequest();
            req.setImageId(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L));
            assertThrows(BaseException.class, () -> postService.uploadPost(req));
        }

        @Test
        void shouldRejectWhenCoverIndexOutOfBounds() {
            UploadPostRequest req = new UploadPostRequest();
            req.setImageId(Arrays.asList(1L, 2L));
            req.setTitle("标题"); req.setContent("内容"); req.setCover(5); req.setIsPrivate(0);
            assertThrows(BaseException.class, () -> postService.uploadPost(req));
        }

        @Test
        void shouldRejectWhenNotLoggedIn() {
            UploadPostRequest req = new UploadPostRequest();
            req.setImageId(Arrays.asList(1L));
            req.setTitle("标题"); req.setContent("内容"); req.setCover(0); req.setIsPrivate(0);
            userHolderMock.when(UserHolder::getUser).thenReturn(null);
            assertThrows(BaseException.class, () -> postService.uploadPost(req));
        }

        @Test
        void shouldUploadSuccessfully() {
            UploadPostRequest req = new UploadPostRequest();
            req.setImageId(Arrays.asList(1L, 2L));
            req.setTitle("标题"); req.setContent("内容"); req.setCover(0); req.setIsPrivate(0);

            Picture pic1 = new Picture(); pic1.setId(1L);
            Picture pic2 = new Picture(); pic2.setId(2L);

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(spaceService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.singletonList(new Space()));
            when(pictureService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Arrays.asList(pic1, pic2));
            doReturn(1).when(postMapper).insert(ArgumentMatchers.<Post>any());
            when(pictureChildService.saveBatch(anyList())).thenReturn(true);

            assertDoesNotThrow(() -> postService.uploadPost(req));
        }
    }

    @Nested
    class LikePostTests {

        @Test
        void shouldRejectWhenPostNotFound() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postMapper.selectById(99L)).thenReturn(null);
            assertThrows(BaseException.class, () -> postService.likePost(99L));
        }

        @Test
        void shouldRejectWhenRedisLockFails() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postMapper.selectById(10L)).thenReturn(testPost);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(10L), any())).thenReturn(false);
            assertThrows(BaseException.class, () -> postService.likePost(10L));
        }

        @Test
        void shouldReturnTrueWhenLiking() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postMapper.selectById(10L)).thenReturn(testPost);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(10L), any())).thenReturn(true);
            when(userPostLikesService.getOne(any(QueryWrapper.class))).thenReturn(null);
            when(userPostLikesService.save(any(UserPostLikes.class))).thenReturn(true);
            when(postMapper.update(any(Post.class), any(QueryWrapper.class))).thenReturn(1);

            boolean result = postService.likePost(10L);

            assertTrue(result);
        }

        @Test
        void shouldReturnFalseWhenUnliking() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postMapper.selectById(10L)).thenReturn(testPost);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(10L), any())).thenReturn(true);

            UserPostLikes existingLike = new UserPostLikes();
            existingLike.setId(100L);
            existingLike.setUserId(1L);
            existingLike.setPostId(10L);
            when(userPostLikesService.getOne(any(QueryWrapper.class))).thenReturn(existingLike);
            when(userPostLikesService.removeById(100L)).thenReturn(true);
            when(postMapper.update(any(Post.class), any(QueryWrapper.class))).thenReturn(1);

            boolean result = postService.likePost(10L);

            assertFalse(result);
        }
    }

    @Nested
    class CollectPostTests {

        @Test
        void shouldRejectWhenPostNotFound() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postMapper.selectById(99L)).thenReturn(null);
            assertThrows(BaseException.class, () -> postService.collectPost(99L));
        }

        @Test
        void shouldRejectWhenRedisLockFails() {
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postMapper.selectById(10L)).thenReturn(testPost);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(10L), any())).thenReturn(false);
            assertThrows(BaseException.class, () -> postService.collectPost(10L));
        }
    }

    @Nested
    class GetPostTests {

        @Test
        void shouldRejectWhenPostNotFound() {
            when(postMapper.selectById(99L)).thenReturn(null);
            assertThrows(BaseException.class, () -> postService.getPost(99L));
        }

        @Test
        void shouldReturnPostDetail() {
            User postUser = new User();
            postUser.setId(2L); postUser.setUsername("author"); postUser.setAvatar("avatar.png");

            when(postMapper.selectById(10L)).thenReturn(testPost);
            when(pictureChildService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
            when(pictureService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());
            when(userService.getById(2L)).thenReturn(postUser);
            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(userPostCollectService.count(any())).thenReturn(0L);
            when(userPostLikesService.count(any())).thenReturn(0L);

            PostDetailVO result = postService.getPost(10L);

            assertEquals("测试帖子", result.getTitle());
            assertEquals("author", result.getUsername());
            assertFalse(result.getIsLiked());
        }
    }

    @Nested
    class EditPostTests {

        @Test
        void shouldRejectWhenNotOwnPost() {
            EditPostRequest req = new EditPostRequest();
            req.setId(10L); req.setImageId(Arrays.asList(1L));
            req.setTitle("修改"); req.setContent("内容"); req.setCover(0); req.setIsPrivate(0);

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postMapper.selectById(10L)).thenReturn(testPost);

            assertThrows(BaseException.class, () -> postService.editPost(req));
        }
    }
}
