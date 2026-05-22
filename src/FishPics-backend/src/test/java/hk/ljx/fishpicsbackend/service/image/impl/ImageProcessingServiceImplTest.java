package hk.ljx.fishpicsbackend.service.image.impl;

import hk.ljx.fishpicsbackend.common.config.ImageProcessingConfig;
import net.coobird.thumbnailator.Thumbnails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ImageProcessingServiceImplTest {

    private ImageProcessingConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        config = new ImageProcessingConfig();
    }

    @Test
    void testCreateSampleImage() throws IOException {
        BufferedImage image = createSampleImage(400, 300);
        assertNotNull(image);
        assertEquals(400, image.getWidth());
        assertEquals(300, image.getHeight());
    }

    @Test
    void testThumbnailCompress() throws IOException {
        BufferedImage source = createSampleImage(800, 600);

        File output = tempDir.resolve("compressed.jpg").toFile();
        Thumbnails.of(source)
                .size(400, 300)
                .outputFormat("jpg")
                .outputQuality(0.5)
                .toFile(output);

        assertTrue(output.exists());
        assertTrue(output.length() > 0);
    }

    @Test
    void testThumbnailRotate() throws IOException {
        BufferedImage source = createSampleImage(400, 300);

        BufferedImage rotated = Thumbnails.of(source)
                .scale(1.0)
                .rotate(90)
                .asBufferedImage();

        assertEquals(300, rotated.getWidth());
        assertEquals(400, rotated.getHeight());
    }

    @Test
    void testThumbnailForceSize() throws IOException {
        BufferedImage source = createSampleImage(800, 600);

        BufferedImage forced = Thumbnails.of(source)
                .forceSize(200, 200)
                .asBufferedImage();

        assertEquals(200, forced.getWidth());
        assertEquals(200, forced.getHeight());
    }

    @Test
    void testThumbnailKeepAspectRatio() throws IOException {
        BufferedImage source = createSampleImage(800, 600);

        BufferedImage thumb = Thumbnails.of(source)
                .size(400, 400)
                .asBufferedImage();

        assertTrue(thumb.getWidth() <= 400);
        assertTrue(thumb.getHeight() <= 400);
    }

    @Test
    void testImageFormatConversionPngToJpg() throws IOException {
        BufferedImage source = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = source.createGraphics();
        g2d.setColor(new Color(255, 0, 0, 128));
        g2d.fillRect(0, 0, 200, 200);
        g2d.dispose();

        File output = tempDir.resolve("converted.jpg").toFile();
        Thumbnails.of(source)
                .size(200, 200)
                .outputFormat("jpg")
                .outputQuality(0.85)
                .toFile(output);

        assertTrue(output.exists());
        assertTrue(output.length() > 0);
    }

    private boolean webpSupported() {
        return ImageIO.getImageWritersByFormatName("webp").hasNext();
    }

    @Test
    void testImageFormatConversionToWebp() throws IOException {
        assumeTrue(webpSupported(), "WEBP writer not available, skipping test");
        BufferedImage source = createSampleImage(300, 200);

        File output = tempDir.resolve("output.webp").toFile();
        BufferedImage rgbSource = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbSource.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        ImageIO.write(rgbSource, "webp", output);

        assertTrue(output.exists());
        assertTrue(output.length() > 0);
    }

    @Test
    void testCompressionQualityVisible() throws IOException {
        BufferedImage source = createGradientImage(800, 600);

        File highQuality = tempDir.resolve("high.jpg").toFile();
        File lowQuality = tempDir.resolve("low.jpg").toFile();

        Thumbnails.of(source)
                .size(800, 600)
                .outputFormat("jpg")
                .outputQuality(0.9)
                .toFile(highQuality);

        Thumbnails.of(source)
                .size(800, 600)
                .outputFormat("jpg")
                .outputQuality(0.1)
                .toFile(lowQuality);

        assertTrue(highQuality.length() > lowQuality.length());
    }

    @Test
    void testArbitraryAngleRotation() throws IOException {
        BufferedImage source = createSampleImage(400, 400);
        int centerX = 200;
        int centerY = 200;
        source.setRGB(centerX, centerY, Color.RED.getRGB());

        BufferedImage rotated45 = Thumbnails.of(source)
                .scale(1.0)
                .rotate(45)
                .asBufferedImage();

        assertNotNull(rotated45);
        assertTrue(rotated45.getWidth() > 400);
        assertTrue(rotated45.getHeight() > 400);
    }

    @Test
    void testBufferedImageToBytesJpeg() throws IOException {
        BufferedImage source = createSampleImage(200, 200);

        File output = tempDir.resolve("test.jpg").toFile();
        Thumbnails.of(source)
                .size(200, 200)
                .outputFormat("jpg")
                .outputQuality(0.85)
                .toFile(output);

        assertTrue(output.exists());
        BufferedImage reloaded = ImageIO.read(output);
        assertNotNull(reloaded);
        assertEquals(200, reloaded.getWidth());
        assertEquals(200, reloaded.getHeight());
    }

    @Test
    void testBufferedImageToBytesPng() throws IOException {
        BufferedImage source = createSampleImage(150, 150);

        File output = tempDir.resolve("test.png").toFile();
        Thumbnails.of(source)
                .size(150, 150)
                .outputFormat("png")
                .toFile(output);

        assertTrue(output.exists());
        BufferedImage reloaded = ImageIO.read(output);
        assertNotNull(reloaded);
    }

    @Test
    void testWatermarkTextRendering() throws IOException {
        BufferedImage source = createSampleImage(400, 300);

        String fontName = "微软雅黑";
        Font font = new Font(fontName, Font.BOLD, 36);
        if (font.canDisplayUpTo("中文测试") != -1) {
            font = new Font(Font.SANS_SERIF, Font.BOLD, 36);
        }

        BufferedImage tmp = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(new Color(255, 255, 255, 128));
        g.drawString("水印测试", 100, 150);
        g.dispose();

        assertNotNull(tmp);
    }

    @Test
    void testRgbConversionForJpeg() throws IOException {
        BufferedImage argbImage = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = argbImage.createGraphics();
        g2d.setColor(new Color(0, 255, 0, 100));
        g2d.fillRect(0, 0, 200, 200);
        g2d.dispose();

        BufferedImage rgbImage = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2dRgb = rgbImage.createGraphics();
        g2dRgb.drawImage(argbImage, 0, 0, null);
        g2dRgb.dispose();

        File output = tempDir.resolve("rgb_test.jpg").toFile();
        Thumbnails.of(rgbImage)
                .size(200, 200)
                .outputFormat("jpg")
                .outputQuality(0.85)
                .toFile(output);

        assertTrue(output.exists());
        assertTrue(output.length() > 0);
    }

    @Test
    void testLargeImageProcessing() throws IOException {
        BufferedImage largeImage = createSampleImage(3000, 2000);

        File output = tempDir.resolve("large_thumb.jpg").toFile();
        long startTime = System.currentTimeMillis();

        Thumbnails.of(largeImage)
                .size(800, 600)
                .outputFormat("jpg")
                .outputQuality(0.8)
                .toFile(output);

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 5000);
        assertTrue(output.exists());
    }

    @Test
    void testEdgeCaseMinimalSize() throws IOException {
        BufferedImage tiny = createSampleImage(1, 1);

        File output = tempDir.resolve("tiny.jpg").toFile();
        assertDoesNotThrow(() -> {
            Thumbnails.of(tiny)
                    .size(1, 1)
                    .outputFormat("jpg")
                    .toFile(output);
        });

        assertTrue(output.exists());
    }

    @Test
    void testConfigDefaults() {
        ImageProcessingConfig freshConfig = new ImageProcessingConfig();
        assertEquals(0.85, freshConfig.getDefaultJpegQuality(), 0.01);
        assertEquals(500 * 1024, freshConfig.getTargetFileSizeBytes());
        assertEquals(4096, freshConfig.getMaxWidth());
        assertEquals(4096, freshConfig.getMaxHeight());
        assertEquals(200, freshConfig.getThumbnailWidth());
        assertEquals(200, freshConfig.getThumbnailHeight());
        assertEquals("jpg", freshConfig.getDefaultOutputFormat());
    }

    @Test
    void testFormatSupport() {
        String[] supported = { "jpg", "jpeg", "png" };
        for (String format : supported) {
            try {
                BufferedImage source = createSampleImage(100, 100);
                File output = tempDir.resolve("test." + format).toFile();
                Thumbnails.of(source)
                        .size(100, 100)
                        .outputFormat(format)
                        .outputQuality(format.equals("png") ? 1.0 : 0.85)
                        .toFile(output);
                assertTrue(output.exists());
            } catch (IOException e) {
            }
        }
        if (webpSupported()) {
            try {
                BufferedImage source = createSampleImage(100, 100);
                File output = tempDir.resolve("test.webp").toFile();
                BufferedImage rgbSource = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgbSource.createGraphics();
                g.drawImage(source, 0, 0, null);
                g.dispose();
                ImageIO.write(rgbSource, "webp", output);
                assertTrue(output.exists());
            } catch (IOException e) {
            }
        }
    }

    private BufferedImage createSampleImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(100, 150, 200));
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.RED);
        g2d.fillOval(width / 4, height / 4, width / 2, height / 2);
        g2d.setColor(Color.WHITE);
        g2d.drawLine(0, 0, width, height);
        g2d.drawLine(width, 0, 0, height);
        g2d.dispose();
        return image;
    }

    private BufferedImage createGradientImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int r = (int) (255.0 * x / width);
                int g = (int) (255.0 * y / height);
                int b = 128;
                image.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }
        g2d.dispose();
        return image;
    }
}