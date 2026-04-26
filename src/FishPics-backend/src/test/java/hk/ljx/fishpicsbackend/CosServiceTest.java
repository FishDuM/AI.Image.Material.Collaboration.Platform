package hk.ljx.fishpicsbackend;

import hk.ljx.fishpicsbackend.common.utils.LimitedInputStream;
import hk.ljx.fishpicsbackend.service.CosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CosServiceTest {

    @Mock
    private com.qcloud.cos.COSClient cosClient;

    @InjectMocks
    private CosService cosService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ==============================
    // 测试1：正常上传图片（JPG）
    // ==============================
    @Test
    void testUploadJpgSuccess() throws IOException {
        byte[] mockImage = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                new ByteArrayInputStream(mockImage)
        );

        // 不会抛异常 = 上传成功
        assertDoesNotThrow(() -> {
            String key = cosService.uploadPicture(file);
            System.out.println("上传成功 key = " + key);
        });
    }

    // ==============================
    // 测试2：正常上传图片（PNG）
    // ==============================
    @Test
    void testUploadPngSuccess() throws IOException {
        byte[] mockImage = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                new ByteArrayInputStream(mockImage)
        );

        assertDoesNotThrow(() -> {
            cosService.uploadPicture(file);
        });
    }

    // ==============================
    // 测试3：上传非图片 → 应该抛异常
    // ==============================
    @Test
    void testUploadNotImage() throws IOException {
        byte[] mockFile = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                new ByteArrayInputStream(mockFile)
        );

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            cosService.uploadPicture(file);
        });

        assertEquals("只能上传 JPG、PNG、GIF、WEBP 格式图片", exception.getMessage());
    }

    // ==============================
    // 测试4：文件为空 → 抛异常
    // ==============================
    @Test
    void testUploadEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(hk.ljx.fishpicsbackend.common.exception.BaseException.class, () -> {
            cosService.uploadPicture(file);
        });
    }

    // ==============================
    // 测试5：文件大小超过5MB → 抛异常
    // ==============================
    @Test
    void testUploadFileTooBig() {
        // 6MB 文件
        byte[] bigFile = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "big.jpg",
                "image/jpeg",
                bigFile
        );

        assertThrows(RuntimeException.class, () -> {
            cosService.uploadPicture(file);
        });
    }

    // ==============================
    // 测试6：文件名不合法（无后缀）
    // ==============================
    @Test
    void testFileNameInvalid() {
        byte[] data = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "testNoSuffix",
                "image/jpeg",
                data
        );

        assertThrows(RuntimeException.class, () -> {
            cosService.uploadPicture(file);
        });
    }
}