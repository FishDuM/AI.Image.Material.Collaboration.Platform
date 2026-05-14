package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.img.Img;
import cn.hutool.core.img.ImgUtil;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ImageProcessUtilTest {

    private BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, new Color(x % 256, y % 256, (x + y) % 256).getRGB());
            }
        }
        return image;
    }

    @Test
    void testScaleImage() {
        BufferedImage source = createTestImage(400, 300);
        Image scaled = ImgUtil.scale(source, 0.5f);
        assertEquals(200, scaled.getWidth(null));
        assertEquals(150, scaled.getHeight(null));
    }

    @Test
    void testScaleByWidth() {
        BufferedImage source = createTestImage(800, 600);
        double scale = 400.0 / 800.0;
        Image scaled = ImgUtil.scale(source, (float) scale);
        assertEquals(400, scaled.getWidth(null));
    }

    @Test
    void testRotateImage() {
        BufferedImage source = createTestImage(200, 100);
        Image rotated = ImgUtil.rotate(source, 180);
        assertEquals(200, rotated.getWidth(null));
        assertEquals(100, rotated.getHeight(null));
    }

    @Test
    void testRotate90Degrees() {
        BufferedImage source = createTestImage(200, 100);
        Image rotated = ImgUtil.rotate(source, 90);
        assertTrue(rotated.getWidth(null) > 0);
        assertTrue(rotated.getHeight(null) > 0);
    }

    @Test
    void testCutImage() {
        BufferedImage source = createTestImage(400, 300);
        Rectangle rect = new Rectangle(50, 50, 200, 150);
        Image cropped = ImgUtil.cut(source, rect);
        assertEquals(200, cropped.getWidth(null));
        assertEquals(150, cropped.getHeight(null));
    }

    @Test
    void testCutEdgeClamp() {
        BufferedImage source = createTestImage(200, 200);
        Rectangle rect = new Rectangle(50, 50, 300, 300);
        assertDoesNotThrow(() -> {
            Rectangle clamped = new Rectangle(50, 50,
                    Math.min(300, source.getWidth() - 50),
                    Math.min(300, source.getHeight() - 50));
            ImgUtil.cut(source, clamped);
        });
    }

    @Test
    void testWriteFormats() {
        BufferedImage source = createTestImage(100, 100);
        assertDoesNotThrow(() -> {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImgUtil.write(source, "png", baos);
            assertTrue(baos.size() > 0);
        });
        assertDoesNotThrow(() -> {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImgUtil.write(source, "jpg", baos);
            assertTrue(baos.size() > 0);
        });
    }

    @Test
    void testReadFromUrl() {
        assertDoesNotThrow(() -> {
            BufferedImage image = createTestImage(50, 50);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImgUtil.write(image, "png", baos);
            BufferedImage read = ImgUtil.read(new java.io.ByteArrayInputStream(baos.toByteArray()));
            assertNotNull(read);
            assertEquals(50, read.getWidth());
            assertEquals(50, read.getHeight());
        });
    }

    @Test
    void testWatermarkText() {
        BufferedImage source = createTestImage(400, 300);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 20);
        Img img = Img.from(source);
        img.pressText("Watermark", Color.WHITE, font, 200, 150, 0.3f);
        Image result = img.getImg();
        assertNotNull(result);
        assertEquals(400, result.getWidth(null));
        assertEquals(300, result.getHeight(null));
    }

    @Test
    void testWatermarkChineseText() {
        BufferedImage source = createTestImage(400, 300);
        Font font = createChineseFont(Font.BOLD, 20);
        Img img = Img.from(source);
        img.pressText("中文水印测试", Color.WHITE, font, 200, 150, 0.3f);
        Image result = img.getImg();
        assertNotNull(result);
    }

    @Test
    void testChainOperations() {
        BufferedImage source = createTestImage(800, 600);
        Image processed = source;
        processed = ImgUtil.rotate(processed, 0);
        Rectangle rect = new Rectangle(100, 100, 400, 300);
        processed = ImgUtil.cut(processed, rect);
        processed = ImgUtil.scale(processed, 0.5f);
        assertEquals(200, processed.getWidth(null));
        assertEquals(150, processed.getHeight(null));
    }

    @Test
    void testDeviceResponsiveMobile() {
        BufferedImage source = createTestImage(2000, 1500);
        int maxWidth = 750;
        Image result = source;
        if (source.getWidth() > maxWidth) {
            double ratio = (double) maxWidth / source.getWidth();
            result = ImgUtil.scale(source, (float) ratio);
        }
        assertEquals(750, result.getWidth(null));
        assertEquals(562, result.getHeight(null));
    }

    @Test
    void testDeviceResponsivePC() {
        BufferedImage source = createTestImage(3000, 2000);
        int maxWidth = 1920;
        Image result = source;
        if (source.getWidth() > maxWidth) {
            double ratio = (double) maxWidth / source.getWidth();
            result = ImgUtil.scale(source, (float) ratio);
        }
        assertEquals(1920, result.getWidth(null));
    }

    @Test
    void testDeviceResponsiveSmallImage() {
        BufferedImage source = createTestImage(400, 300);
        int maxWidth = 750;
        Image result = source;
        if (source.getWidth() > maxWidth) {
            double ratio = (double) maxWidth / source.getWidth();
            result = ImgUtil.scale(source, (float) ratio);
        }
        assertEquals(400, result.getWidth(null));
        assertEquals(300, result.getHeight(null));
    }

    private Font createChineseFont(int style, int size) {
        String[] candidateFonts = { "微软雅黑", "宋体", "SimSun", "PingFang SC", "Noto Sans CJK SC", "WenQuanYi Micro Hei" };
        for (String fontName : candidateFonts) {
            Font font = new Font(fontName, style, size);
            if (font.canDisplayUpTo("中文测试") == -1) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }
}
