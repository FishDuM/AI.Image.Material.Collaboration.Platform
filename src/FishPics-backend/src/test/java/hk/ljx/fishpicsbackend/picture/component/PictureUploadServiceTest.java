package hk.ljx.fishpicsbackend.picture.component;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PictureUploadService 单元测试
 * 聚焦参数校验和权限检查逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PictureUploadServiceTest {

    @InjectMocks
    private PictureUploadService uploadService;

    @Mock
    private CosService cosService;
    @Mock
    private PictureMapper pictureMapper;
    @Mock
    private SpaceService spaceService;
    @Mock
    private UserService userService;
    @Mock
    private FileResourceService fileResourceService;
    @Mock
    private SpaceQuotaManager quotaManager;
    @Mock
    private SpaceWritePermissionChecker spaceWritePermissionChecker;

    @BeforeEach
    void setUp() {
        setLoginUser(1L, 0);
    }

    @AfterEach
    void cleanup() {
        UserHolder.removeLoginContext();
    }

    private void setLoginUser(Long userId, int level) {
        UserHolder.setLoginContext(LoginContext.builder()
                .userId(userId)
                .username("testuser")
                .status(1)
                .level(level)
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
                .systemPerms(java.util.List.of("system:user:manage"))
                .build());
    }

    // ==================== uploadAvatar ====================

    @Test
    @DisplayName("uploadAvatar - 未登录应抛异常")
    void uploadAvatar_notLoggedIn_throws() {
        UserHolder.removeLoginContext();
        MultipartFile file = mock(MultipartFile.class);
        assertThrows(BaseException.class, () -> uploadService.uploadAvatar(file, 1L));
    }

    @Test
    @DisplayName("uploadAvatar - 文件超过 5MB 应抛异常")
    void uploadAvatar_fileTooLarge_throws() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(6L * 1024 * 1024);
        when(file.getOriginalFilename()).thenReturn("big.jpg");
        when(file.getContentType()).thenReturn("image/jpeg");

        assertThrows(BaseException.class, () -> uploadService.uploadAvatar(file, 1L));
    }

    @Test
    @DisplayName("uploadAvatar - 不支持的文件类型应抛异常")
    void uploadAvatar_invalidFileType_throws() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("test.exe");
        when(file.getContentType()).thenReturn("application/octet-stream");

        assertThrows(BaseException.class, () -> uploadService.uploadAvatar(file, 1L));
    }

    @Test
    @DisplayName("uploadAvatar - 修改他人头像且无管理员权限应抛异常")
    void uploadAvatar_otherUser_noAdminPerm_throws() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("test.jpg");
        when(file.getContentType()).thenReturn("image/jpeg");

        BaseException ex = assertThrows(BaseException.class,
                () -> uploadService.uploadAvatar(file, 999L));
        assertTrue(ex.getMessage().contains("权限"));
    }

    // ==================== uploadPicture ====================

    @Test
    @DisplayName("uploadPicture - 未登录应抛异常")
    void uploadPicture_notLoggedIn_throws() {
        UserHolder.removeLoginContext();
        MultipartFile file = mock(MultipartFile.class);
        assertThrows(BaseException.class, () -> uploadService.uploadPicture(file, null));
    }

    @Test
    @DisplayName("uploadPicture - 普通用户文件超过等级限制应抛异常")
    void uploadPicture_exceedsLevelLimit_throws() {
        setLoginUser(1L, 0);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(60L * 1024 * 1024);
        when(file.getOriginalFilename()).thenReturn("big.jpg");

        assertThrows(BaseException.class, () -> uploadService.uploadPicture(file, null));
    }

    @Test
    @DisplayName("uploadPicture - 文件超过 100MB 直传限制应抛异常")
    void uploadPicture_exceedsDirectLimit_throws() {
        setLoginUser(1L, 2);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(150L * 1024 * 1024);
        when(file.getOriginalFilename()).thenReturn("huge.jpg");

        assertThrows(BaseException.class, () -> uploadService.uploadPicture(file, null));
    }

    @Test
    @DisplayName("uploadPicture - 不支持的文件格式应抛异常")
    void uploadPicture_invalidFormat_throws() {
        setLoginUser(1L, 1);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("test.exe");
        when(file.getContentType()).thenReturn("application/octet-stream");

        assertThrows(BaseException.class, () -> uploadService.uploadPicture(file, null));
    }

}
