package hk.ljx.fishpicsbackend.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "image.processing")
public class ImageProcessingConfig {

    private double defaultJpegQuality = 0.85;

    private long targetFileSizeBytes = 500 * 1024;

    private int maxWidth = 4096;

    private int maxHeight = 4096;

    private int thumbnailWidth = 200;

    private int thumbnailHeight = 200;

    private int watermarkFontSize = 36;

    private float watermarkOpacity = 0.5f;

    private String defaultOutputFormat = "jpg";

    private int connectionTimeout = 10000;

    private int readTimeout = 30000;
}