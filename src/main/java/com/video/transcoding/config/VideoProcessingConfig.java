package com.video.transcoding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "video.processing")
public class VideoProcessingConfig {
    private String tempDir;
    private List<Quality> qualities;

    @Data
    public static class Quality {
        private String name;
        private int height;
        private String bitrate;
        private String preset;
        private int crf;
    }
} 