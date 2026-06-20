package hk.ljx.fishpicsbackend.picture.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.picture.component.*;
import hk.ljx.fishpicsbackend.common.infra.DistributedLockService;
import hk.ljx.fishpicsbackend.picture.dto.AdminPictureListDTO;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdListRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureQueryRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PictureServiceImpl 单元测试
 * 使用 Mockito mock 所有外部依赖，聚焦业务逻辑
 */
@ExtendWith(MockitoExtension.class)
class PictureServiceImplTest {

    @InjectMocks
    private PictureServiceImpl pictureService;

    @Mock
    private PictureMapper pictureMapper;

    @Mock
    private UserService userService;

    @Mock
    private PictureUploadService pictureUploadService;

    @Mock
    private PictureDeleteManager pictureDeleteManager;

    @Mock
    private PictureReplaceManager pictureReplaceManager;

    @Mock
    private PictureTagManager pictureTagManager;

    @Mock
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Mock
    private DistributedLockService distributedLockService;

    @BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 通过反射注入
        ReflectionTestUtils.setField(pictureService, "baseMapper", pictureMapper);
    }

    @AfterEach
    void cleanup() {
        UserHolder.removeLoginContext();
    }

    private void setLoginUser(Long userId) {
        UserHolder.setLoginContext(LoginContext.builder()
                .userId(userId)
                .username("testuser")
                .status(1)
                .level(0)
                .role(0)
                .build());
    }

    private void setLoginAdmin(Long userId) {
        UserHolder.setLoginContext(LoginContext.builder()
                .userId(userId)
                .username("admin")
                .status(1)
                .level(0)
                .role(1)
                .isAdmin(true)
                .build());
    }

    // ==================== getPictureList ====================

    @Test
    @DisplayName("getPictureList - 正常查询应返回已审核、非私密图片")
    void getPictureList_normalQuery_returnsApprovedPictures() {
        PictureQueryRequest request = new PictureQueryRequest();
        request.setCurrent(1);
        request.setPageSize(10);
        request.setTag("");

        Picture pic = new Picture();
        pic.setId(1L);
        pic.setUrl("http://example.com/1.jpg");

        Page<Picture> page = new Page<>(1, 10);
        page.setRecords(List.of(pic));
        page.setTotal(1);

        when(pictureMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
        when(pictureTagManager.batchLoadTags(anyList()))
                .thenReturn(Map.of(1L, List.of("风景", "自然")));

        IPage<PictureVO> result = pictureService.getPictureList(request);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals("http://example.com/1.jpg", result.getRecords().get(0).getUrl());
        verify(pictureTagManager).batchLoadTags(List.of(1L));
    }

    @Test
    @DisplayName("getPictureList - tag 过滤应走子查询")
    void getPictureList_withTag_usesSubquery() {
        PictureQueryRequest request = new PictureQueryRequest();
        request.setCurrent(1);
        request.setPageSize(10);
        request.setTag("风景");

        Page<Picture> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());

        when(pictureTagManager.findPictureIdsByTag("风景")).thenReturn(Collections.emptyList());

        IPage<PictureVO> result = pictureService.getPictureList(request);

        assertNotNull(result);
        verify(pictureTagManager).findPictureIdsByTag("风景");
    }

    @Test
    @DisplayName("getPictureList - 指定 tag 且无匹配图片时应返回空页")
    void getPictureList_tagNoMatch_returnsEmpty() {
        PictureQueryRequest request = new PictureQueryRequest();
        request.setCurrent(1);
        request.setPageSize(10);
        request.setTag("不存在的标签");

        when(pictureTagManager.findPictureIdsByTag("不存在的标签"))
                .thenReturn(Collections.emptyList());

        IPage<PictureVO> result = pictureService.getPictureList(request);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
        verify(pictureMapper, never()).selectPage(any(), any());
    }

    // ==================== getAdminPictureList ====================

    @Test
    @DisplayName("getAdminPictureList - selected=1 应筛选已精选图片")
    void getAdminPictureList_selected1_filtersFeatured() {
        AdminPictureListDTO dto = new AdminPictureListDTO();
        dto.setCurrent(1);
        dto.setPageSize(10);
        dto.setSelected(1);

        Page<Picture> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());

        when(pictureMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage);
        when(pictureTagManager.batchLoadTags(anyList())).thenReturn(Collections.emptyMap());

        IPage<PictureVO> result = pictureService.getAdminPictureList(dto);

        assertNotNull(result);
        verify(pictureMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("getAdminPictureList - selected=2 应筛选精选待审核")
    void getAdminPictureList_selected2_filtersPendingFeatured() {
        AdminPictureListDTO dto = new AdminPictureListDTO();
        dto.setCurrent(1);
        dto.setPageSize(10);
        dto.setSelected(2);

        Page<Picture> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());

        when(pictureMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(emptyPage);
        when(pictureTagManager.batchLoadTags(anyList())).thenReturn(Collections.emptyMap());

        IPage<PictureVO> result = pictureService.getAdminPictureList(dto);

        assertNotNull(result);
    }

    // ==================== reviewPicture ====================

    @Test
    @DisplayName("reviewPicture - pictureId 为 null 时应抛异常")
    void reviewPicture_nullId_throws() {
        assertThrows(BaseException.class, () -> pictureService.reviewPicture(null, 1));
    }

    @Test
    @DisplayName("reviewPicture - 图片不存在时应抛异常")
    void reviewPicture_notFound_throws() {
        when(pictureMapper.selectById(999L)).thenReturn(null);
        assertThrows(BaseException.class, () -> pictureService.reviewPicture(999L, 1));
    }

    @Test
    @DisplayName("reviewPicture - 设为精选应成功")
    void reviewPicture_setFeatured_success() {
        Picture pic = new Picture();
        pic.setId(1L);
        pic.setIsSelected(0);

        when(pictureMapper.selectById(1L)).thenReturn(pic);
        when(pictureMapper.updateById(any(Picture.class))).thenReturn(1);

        pictureService.reviewPicture(1L, 1);

        assertEquals(1, pic.getIsSelected());
        verify(pictureMapper).updateById(pic);
    }

    @Test
    @DisplayName("reviewPicture - selected 值无效（非 0/1）时应抛异常")
    void reviewPicture_invalidSelected_throws() {
        Picture pic = new Picture();
        pic.setId(1L);
        pic.setIsSelected(0);

        when(pictureMapper.selectById(1L)).thenReturn(pic);

        assertThrows(BaseException.class, () -> pictureService.reviewPicture(1L, 3));
    }

    // ==================== updatePicture ====================

    @Test
    @DisplayName("updatePicture - 未登录时应抛异常")
    void updatePicture_notLoggedIn_throws() {
        PictureUpdateRequest request = new PictureUpdateRequest();
        request.setId(1L);
        assertThrows(BaseException.class, () -> pictureService.updatePicture(request));
    }

    @Test
    @DisplayName("updatePicture - 图片不存在时应抛异常")
    void updatePicture_pictureNotFound_throws() {
        setLoginUser(1L);
        when(pictureMapper.selectById(1L)).thenReturn(null);

        PictureUpdateRequest request = new PictureUpdateRequest();
        request.setId(1L);

        assertThrows(BaseException.class, () -> pictureService.updatePicture(request));
    }

    // ==================== deletePicture ====================

    @Test
    @DisplayName("deletePicture - 应委托给 PictureDeleteManager")
    void deletePicture_delegatesToManager() {
        DeleteByIdListRequest request = new DeleteByIdListRequest();
        request.setIds(List.of(1L, 2L));

        when(pictureDeleteManager.delete(request)).thenReturn("删除成功");

        String result = pictureService.deletePicture(request);

        assertEquals("删除成功", result);
        verify(pictureDeleteManager).delete(request);
    }

    // ==================== 委托方法 ====================

    @Test
    @DisplayName("uploadAvatar - 应委托给 PictureUploadManager")
    void uploadAvatar_delegatesToManager() {
        var mockFile = mock(org.springframework.web.multipart.MultipartFile.class);
        when(pictureUploadService.uploadAvatar(mockFile, 1L)).thenReturn("http://url");

        String result = pictureService.uploadAvatar(mockFile, 1L);
        assertEquals("http://url", result);
        verify(pictureUploadService).uploadAvatar(mockFile, 1L);
    }

    @Test
    @DisplayName("checkUpload - 应委托给 PictureUploadManager")
    void checkUpload_delegatesToManager() {
        var request = new hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest();
        var vo = new hk.ljx.fishpicsbackend.picture.vo.CheckUploadVO();
        when(pictureUploadService.checkUpload(request)).thenReturn(vo);

        assertSame(vo, pictureService.checkUpload(request));
    }
}
