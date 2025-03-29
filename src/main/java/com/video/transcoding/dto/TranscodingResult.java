package com.video.transcoding.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TranscodingResult {
    private String videoId;
    private String userId;
    private boolean success;
    private String errorMessage;
    private LocalDateTime completedAt;
    private List<VideoQuality> qualities;

    @Data
    public static class VideoQuality {
        private String name;
        private int height;
        private String bitrate;
        private String objectName;
    }
} 