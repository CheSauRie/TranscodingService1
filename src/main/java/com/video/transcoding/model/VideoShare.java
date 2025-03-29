package com.video.transcoding.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "video_shares")
public class VideoShare {
    @Id
    private String id;
    private String videoId;
    private String sharedByUserId;
    private String sharedWithUsername;
    private String sharedWithIp;
    private Organization sharedWithOrganization;
    private boolean isSameOrganization;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isActive;
} 