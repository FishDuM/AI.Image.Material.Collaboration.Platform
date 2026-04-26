package hk.ljx.fishpicsbackend.common.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedInputStream extends FilterInputStream {
    private final long maxSize;
    private long current = 0;

    public LimitedInputStream(InputStream in, long maxSize) {
        super(in);
        this.maxSize = maxSize;
    }

    @Override
    public int read() throws IOException {
        int data = super.read();
        if (data == -1) return -1;

        current++;
        checkSize();
        return data;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = super.read(b, off, len);
        if (read == -1) return -1;

        current += read;
        checkSize();
        return read;
    }

    private void checkSize() {
        if (current > maxSize) {
            throw new RuntimeException("文件大小超出限制，最大允许：" + maxSize / 1024 / 1024 + "MB");
        }
    }
}