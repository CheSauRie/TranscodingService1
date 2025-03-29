package com.video.transcoding.dto;

import com.video.transcoding.model.Organization;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareSyncRequest {
    private String videoId;
    private String sharedByUserId;
    private String sharedWithUsername;
    private String sharedWithIp;
    private Organization sourceOrganization;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
} 