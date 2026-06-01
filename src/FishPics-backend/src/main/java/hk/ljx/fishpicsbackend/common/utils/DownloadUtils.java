package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.core.io.FileUtil;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadUtils {

    /**
     * 从 URL 下载文件到临时文件，流式写入，最大 maxSize 字节
     *
     * @param url     文件URL
     * @param maxSize 最大允许字节数
     * @return 下载好的临时文件（调用方负责删除）
     */
    public static File download(String url, long maxSize) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(120_000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FishPics/1.0)");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR,
                        "图片下载失败，服务器返回 " + responseCode);
            }

            // 预检 Content-Length
            long contentLength = conn.getContentLengthLong();
            if (contentLength > maxSize) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR,
                        "文件大小超过限制（最大 " + (maxSize / 1024 / 1024) + "MB）");
            }

            File tempFile = FileUtil.createTempFile();

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new FileOutputStream(tempFile)) {
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > maxSize) {
                        FileUtil.del(tempFile);
                        throw new BaseException(ExceptionCode.PARAMETER_ERROR,
                                "文件大小超过限制（最大 " + (maxSize / 1024 / 1024) + "MB）");
                    }
                    out.write(buf, 0, n);
                }
            }

            return tempFile;

        } catch (BaseException e) {
            throw e;
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "图片下载失败：" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
