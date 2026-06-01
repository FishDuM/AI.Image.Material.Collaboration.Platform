package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.core.io.FileTypeUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;

public class FileTypeUtils {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "jpg", "jpeg", "png", "bmp", "webp", "tiff", "gif",
            "avif", "heic", "heif", "apng", "astc", "tpg",
            "psd", "ai", "eps",
            "raw", "dng", "cr3", "crw", "mos", "erf", "3fr", "fff",
            "kdc", "dcr", "rw2", "pef", "sr2", "srf", "arw", "nef",
            "nrw", "orf", "mef", "mrw"
    );

    /**
     * 从 MultipartFile 检测合法文件类型（魔数检测 + 扩展名回退）
     */
    public static String getValidFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            String realType = FileTypeUtil.getType(in);
            if (realType != null) {
                realType = realType.toLowerCase();
                if (ALLOWED_TYPES.contains(realType)) {
                    return realType;
                }
            }
            // 魔数检测失败（如 HEIC 等 Hutool 未覆盖的格式），回退到扩展名判断
            String fileName = file.getOriginalFilename();
            if (fileName != null) {
                int dot = fileName.lastIndexOf('.');
                if (dot > 0) {
                    String ext = fileName.substring(dot + 1).toLowerCase();
                    if (ALLOWED_TYPES.contains(ext)) {
                        return ext;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 InputStream 检测合法文件类型（仅魔数检测，无扩展名回退）
     */
    public static String getValidFileType(InputStream inputStream) {
        if (inputStream == null) return null;
        try {
            String realType = FileTypeUtil.getType(inputStream);
            if (realType != null) {
                realType = realType.toLowerCase();
                if (ALLOWED_TYPES.contains(realType)) {
                    return realType;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
