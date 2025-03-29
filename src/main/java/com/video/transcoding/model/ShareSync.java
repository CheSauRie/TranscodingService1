package com.video.transcoding.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "share_syncs")
public class ShareSync {
    @Id
    private String id;
    private String videoId;
    private String sharedByUserId;
    private String sharedWithUsername;
    private String sharedWithIp;
    private Organization sourceOrganization;
    private Organization targetOrganization;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isProcessed;
    private String status; // PENDING, PROCESSED, FAILED
    private String errorMessage;
} 