package hk.ljx.fishpicsbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdList;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.impl.PictureServiceImpl;
import hk.ljx.fishpicsbackend.post.entity.Post;
import hk.ljx.fishpicsbackend.post.service.PostService;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PictureServiceImplTest {

    @Mock private CosService cosService;
    @Mock private PictureMapper pictureMapper;
    @Mock private SpaceService spaceService;
    @Mock private UserService userService;
    @Mock private PostService postService;

    @InjectMocks
    private PictureServiceImpl pictureService;

    private MockedStatic<UserHolder> userHolderMock;
    private User testUser;

    @BeforeEach
    void setUp() {
        userHolderMock = mockStatic(UserHolder.class);
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setRole("user");
        testUser.setLevel(0);

        ReflectionTestUtils.setField(pictureService, "baseMapper", pictureMapper);
    }

    @AfterEach
    void tearDown() {
        userHolderMock.close();
    }

    @Nested
    class DeletePictureTests {

        @Test
        void shouldRejectWhenIdsEmpty() {
            DeleteByIdList req = new DeleteByIdList();
            req.setIds(Collections.emptyList());

            assertThrows(BaseException.class, () -> pictureService.deletePicture(req));
        }

        @Test
        void shouldRejectWhenNotLoggedIn() {
            DeleteByIdList req = new DeleteByIdList();
            req.setIds(Arrays.asList(1L));

            userHolderMock.when(UserHolder::getUser).thenReturn(null);

            assertThrows(BaseException.class, () -> pictureService.deletePicture(req));
        }

        @Test
        void shouldRejectWhenNoPermission() {
            DeleteByIdList req = new DeleteByIdList();
            req.setIds(Arrays.asList(1L));

            Picture picture = new Picture();
            picture.setId(1L);
            picture.setUserId(999L);

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(pictureMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(picture));

            assertThrows(BaseException.class, () -> pictureService.deletePicture(req));
        }

        @Test
        void shouldAllowAdminDeleteAnyPicture() {
            testUser.setRole(ADMIN);
            DeleteByIdList req = new DeleteByIdList();
            req.setIds(Arrays.asList(1L));

            Picture picture = new Picture();
            picture.setId(1L);
            picture.setUserId(999L);
            picture.setUrl("https://cos.example.com/pic1.jpg");

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(pictureMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(picture));
            when(pictureMapper.delete(any(QueryWrapper.class))).thenReturn(1);
            doNothing().when(cosService).deletePictureByUrl(anyString());

            String result = pictureService.deletePicture(req);

            assertEquals("删除成功", result);
        }

        @Test
        void shouldAllowOwnerDeleteTheirPicture() {
            DeleteByIdList req = new DeleteByIdList();
            req.setIds(Arrays.asList(1L));

            Picture picture = new Picture();
            picture.setId(1L);
            picture.setUserId(1L);
            picture.setUrl("https://cos.example.com/pic1.jpg");

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
            when(pictureMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(picture));
            when(pictureMapper.delete(any(QueryWrapper.class))).thenReturn(1);
            doNothing().when(cosService).deletePictureByUrl(anyString());

            String result = pictureService.deletePicture(req);

            assertEquals("删除成功", result);
        }

        @Test
        void shouldSkipCoverImages() {
            DeleteByIdList req = new DeleteByIdList();
            req.setIds(new java.util.ArrayList<>(Arrays.asList(1L, 2L)));

            Post coverPost = Post.builder().cover(1L).build();

            Picture picture = new Picture();
            picture.setId(2L);
            picture.setUserId(1L);
            picture.setUrl("https://cos.example.com/pic2.jpg");

            userHolderMock.when(UserHolder::getUser).thenReturn(testUser);
            when(postService.list(any(QueryWrapper.class))).thenReturn(Collections.singletonList(coverPost));
            when(pictureMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(picture));
            when(pictureMapper.delete(any(QueryWrapper.class))).thenReturn(1);
            doNothing().when(cosService).deletePictureByUrl(anyString());

            String result = pictureService.deletePicture(req);

            assertTrue(result.contains("封面"));
        }
    }

    @Nested
    class ReviewPictureTests {

        @Test
        void shouldRejectWhenPictureIdIsNull() {
            assertThrows(BaseException.class, () -> pictureService.reviewPicture(null, 1, null));
        }

        @Test
        void shouldRejectWhenPictureNotFound() {
            when(pictureMapper.selectById(99L)).thenReturn(null);

            assertThrows(BaseException.class, () -> pictureService.reviewPicture(99L, 1, null));
        }

        @Test
        void shouldRejectInvalidStatus() {
            Picture picture = new Picture();
            picture.setId(1L);

            when(pictureMapper.selectById(1L)).thenReturn(picture);

            assertThrows(BaseException.class, () -> pictureService.reviewPicture(1L, 3, null));
        }

        @Test
        void shouldApprovePicture() {
            Picture picture = new Picture();
            picture.setId(1L);
            picture.setStatus(2);

            when(pictureMapper.selectById(1L)).thenReturn(picture);
            when(pictureMapper.updateById(any(Picture.class))).thenReturn(1);

            pictureService.reviewPicture(1L, 1, null);

            assertEquals(1, picture.getStatus());
        }

        @Test
        void shouldSetIsPrivate() {
            Picture picture = new Picture();
            picture.setId(1L);
            picture.setStatus(2);

            when(pictureMapper.selectById(1L)).thenReturn(picture);
            when(pictureMapper.updateById(any(Picture.class))).thenReturn(1);

            pictureService.reviewPicture(1L, null, 1);

            assertEquals(1, picture.getIsPrivate());
        }
    }
}
