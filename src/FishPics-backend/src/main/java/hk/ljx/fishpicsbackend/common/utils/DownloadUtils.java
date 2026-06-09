package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.core.io.FileUtil;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;

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

public class DownloadUtils {

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
                inputStream.close();
            } finally {
                connection.disconnect();
            }
        }
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
            HttpURLConnection conn = openConnectionWithValidatedIp(currentUrl, validatedAddress);
            configureConnection(conn);
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

    private static void configureConnection(HttpURLConnection conn) throws IOException {
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FishPics/1.0)");
    }

    private static HttpURLConnection openConnectionWithValidatedIp(String urlStr, InetAddress validatedAddress) throws IOException {
        // SSRF 防护已由 validateUrl() 完成（私网/回环/链路本地地址检查）
        // 此处直接用原始 URL 连接，保留域名以便 HTTPS 的 SNI/TLS 握手正常工作
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection(Proxy.NO_PROXY);
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
            if (ip.startsWith("169.254.") || ip.equals("fd00:ec2::254")) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "metadata endpoint access is forbidden");
            }
            return address;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "invalid url: " + e.getMessage());
        }
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
