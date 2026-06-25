package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;

@Slf4j
public final class DownloadUtils {

    private DownloadUtils() {}

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REDIRECTS = 5;

    public static File download(String url, long maxSize) {
        try (RemoteFileStream remoteFile = openRemoteFile(url, maxSize)) {
            File tempFile = FileUtil.createTempFile();
            try (OutputStream out = new FileOutputStream(tempFile)) {
                copyWithLimit(remoteFile.getInputStream(), out, maxSize);
            } catch (Exception e) {
                FileUtil.del(tempFile);
                throw e;
            }
            return tempFile;
        } catch (BaseException e) {
            throw e;
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "image download failed: " + e.getMessage());
        }
    }

    public static RemoteFileStream openRemoteFile(String url, long maxSize) {
        HttpURLConnection conn = null;
        try {
            conn = openValidatedConnectionWithRedirects(url, maxSize);
            long contentLength = conn.getContentLengthLong();
            if (contentLength > 0 && contentLength > maxSize) {
                conn.disconnect();
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "file size exceeds limit");
            }
            InputStream inputStream = new BufferedInputStream(conn.getInputStream());
            String contentType = conn.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }
            return new RemoteFileStream(
                    conn,
                    inputStream,
                    contentType,
                    contentLength > 0 ? contentLength : null,
                    extractFileName(conn, url)
            );
        } catch (BaseException e) {
            if (conn != null) {
                conn.disconnect();
            }
            throw e;
        } catch (IOException e) {
            if (conn != null) {
                conn.disconnect();
            }
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "image download failed: " + e.getMessage());
        }
    }

    public static void copyToOutputWithLimit(InputStream in, OutputStream out, long maxSize) throws IOException {
        copyWithLimit(in, out, maxSize);
    }

    public static class RemoteFileStream implements AutoCloseable {
        private final HttpURLConnection connection;
        private final InputStream inputStream;
        private final String contentType;
        private final Long contentLength;
        private final String fileName;

        public RemoteFileStream(HttpURLConnection connection, InputStream inputStream,
                                String contentType, Long contentLength, String fileName) {
            this.connection = connection;
            this.inputStream = inputStream;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.fileName = fileName;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public String getContentType() {
            return contentType;
        }

        public Long getContentLength() {
            return contentLength;
        }

        public String getFileName() {
            return fileName;
        }

        @Override
        public void close() throws IOException {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } finally {
                connection.disconnect();
            }
        }
    }

    public static String resolveContentType(String contentType) {
        if (StrUtil.isBlank(contentType)) {
            return "application/octet-stream";
        }
        return contentType;
    }

    public static String defaultFileName(String fileName, String contentType) {
        String name = StrUtil.blankToDefault(fileName, "image");
        if (name.contains(".")) {
            return name;
        }
        String ext = extensionFromContentType(contentType);
        return ext != null ? name + ext : name;
    }

    public static String defaultFileName(String fileName) {
        return defaultFileName(fileName, null);
    }

    private static String extensionFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            case "image/bmp" -> ".bmp";
            case "image/tiff" -> ".tiff";
            case "image/avif" -> ".avif";
            default -> null;
        };
    }

    private static void copyWithLimit(InputStream in, OutputStream out, long maxSize) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxSize) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "file size exceeds limit");
            }
            out.write(buf, 0, n);
        }
    }

    private static HttpURLConnection openValidatedConnectionWithRedirects(String url, long maxSize) throws IOException {
        String currentUrl = url;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            InetAddress validatedAddress = validateUrl(currentUrl);
            // openConnectionWithValidatedIp 内部已调 configureConnection(带 Host 头)
            HttpURLConnection conn = openConnectionWithValidatedIp(currentUrl, validatedAddress);
            int responseCode = conn.getResponseCode();
            if (!isRedirect(responseCode)) {
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect();
                    throw new BaseException(ExceptionCode.PARAMETER_ERROR, "image download failed, status=" + responseCode);
                }
                long contentLength = conn.getContentLengthLong();
                if (contentLength > 0 && contentLength > maxSize) {
                    conn.disconnect();
                    throw new BaseException(ExceptionCode.PARAMETER_ERROR, "file size exceeds limit");
                }
                return conn;
            }

            if (redirectCount == MAX_REDIRECTS) {
                conn.disconnect();
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "too many redirects");
            }

            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location == null || location.isBlank()) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "redirect location is empty");
            }
            currentUrl = URI.create(currentUrl).resolve(location).toString();
        }
        throw new BaseException(ExceptionCode.PARAMETER_ERROR, "invalid redirect chain");
    }

    private static void configureConnection(HttpURLConnection conn, String originalHost) throws IOException {
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FishPics/1.0)");
        // DNS rebinding TOCTOU:连接目标已用 validatedAddress 替换为 IP,
        // 但 HTTP/1.1 虚拟主机需要 Host 头带原 hostname
        if (originalHost != null && !originalHost.isBlank()) {
            conn.setRequestProperty("Host", originalHost);
        }
    }

    private static HttpURLConnection openConnectionWithValidatedIp(String urlStr, InetAddress validatedAddress) throws IOException {
        // IP校验通过后直接用原URL连接。不替换host为IP的原因是：
        // TLS SNI会在ClientHello里传IP而非域名，CDN/服务器不知道用哪个证书→握手失败。
        // DNS rebinding的TOCTOU窗口极小，对下载AI图片这个场景可以接受。
        URI uri = URI.create(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
        configureConnection(conn, uri.getHost());
        return conn;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static InetAddress validateUrl(String urlStr) {
        try {
            URI uri = URI.create(urlStr);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "only http/https urls are allowed");
            }
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "invalid url");
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "private network access is forbidden");
            }
            if (address instanceof Inet6Address inet6Address) {
                byte[] rawAddr = inet6Address.getAddress();
                boolean isMappedV4 = true;
                for (int i = 0; i < 10; i++) {
                    if (rawAddr[i] != 0) {
                        isMappedV4 = false;
                        break;
                    }
                }
                if (isMappedV4 && rawAddr[10] == (byte) 0xff && rawAddr[11] == (byte) 0xff) {
                    InetAddress mappedV4 = InetAddress.getByAddress(
                            new byte[]{rawAddr[12], rawAddr[13], rawAddr[14], rawAddr[15]});
                    if (mappedV4.isLoopbackAddress() || mappedV4.isSiteLocalAddress()
                            || mappedV4.isLinkLocalAddress()) {
                        throw new BaseException(ExceptionCode.PARAMETER_ERROR, "private network access is forbidden");
                    }
                }
            }
            String ip = address.getHostAddress();
            // 云厂商 metadata 内网 IP 黑名单
            if (isCloudMetadataIp(ip)) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "metadata endpoint access is forbidden");
            }
            return address;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "invalid url: " + e.getMessage());
        }
    }

    // 云厂商 metadata 内网 IP
    private static boolean isCloudMetadataIp(String ip) {
        if (ip == null) return false;
        if (ip.equals("169.254.169.254")) return true;            // AWS / Azure / GCP / 华为云 / 腾讯云
        if (ip.equals("100.100.100.200")) return true;            // 阿里云
        if (ip.equals("100.64.0.0")) return true;                // 部分阿里内网(防误中)
        if (ip.startsWith("100.64.")) return true;                // 阿里内部 100.64.0.0/10
        if (ip.equals("169.254.0.23")) return true;              // 腾讯云部分 metadata
        if (ip.equals("fd00:ec2::254")) return true;             // AWS IPv6
        return false;
    }

    private static String extractFileName(HttpURLConnection conn, String url) {
        String contentDisposition = conn.getHeaderField("Content-Disposition");
        if (contentDisposition != null) {
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.startsWith("filename=")) {
                    return trimmed.substring("filename=".length()).replace("\"", "");
                }
                if (trimmed.startsWith("filename*=")) {
                    int idx = trimmed.indexOf("''");
                    if (idx >= 0 && idx + 2 < trimmed.length()) {
                        return trimmed.substring(idx + 2);
                    }
                }
            }
        }
        String path = URI.create(url).getPath();
        if (path == null || path.isBlank()) {
            return "download";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : "download";
    }
}
