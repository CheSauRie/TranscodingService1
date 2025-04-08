package com.video.transcoding.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VideoSyncRequest {
    private String videoId;
    private String sharedByUserId;
    private String sharedWithUsername;
    private String sharedWithIp;
    private String sourceOrganization;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private List<VideoQuality> qualities;
    
    @Data
    public static class VideoQuality {
        private String name;
        private String objectName;
        private String contentType;
        private long size;
    }
} 