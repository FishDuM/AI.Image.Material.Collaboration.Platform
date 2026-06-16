package hk.ljx.fishpicsbackend.picture.vo;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;

@Getter
public class ShareFileVO implements AutoCloseable {

    private final String pictureName;

    private final String contentType;

    private final Long contentLength;

    private final InputStream inputStream;

    private final AutoCloseable closeable;

    public ShareFileVO(String pictureName, String contentType, Long contentLength,
                       InputStream inputStream, AutoCloseable closeable) {
        this.pictureName = pictureName;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.inputStream = inputStream;
        this.closeable = closeable;
    }

    @Override
    public void close() throws IOException {
        try {
            closeable.close();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("close share stream failed", e);
        }
    }
}
