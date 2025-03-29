package com.video.transcoding.controller;

import com.video.transcoding.dto.ShareSyncRequest;
import com.video.transcoding.model.Organization;
import com.video.transcoding.model.VideoShare;
import com.video.transcoding.repository.VideoShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/videos/share/sync")
@RequiredArgsConstructor
public class ShareSyncController {
    private final VideoShareRepository videoShareRepository;
    private final Organization currentOrg;

    @PostMapping
    public ResponseEntity<Void> syncShare(@RequestBody ShareSyncRequest request) {
        try {
            // Create VideoShare in current organization
            VideoShare share = new VideoShare();
            share.setId(UUID.randomUUID().toString());
            share.setVideoId(request.getVideoId());
            share.setSharedByUserId(request.getSharedByUserId());
            share.setSharedWithUsername(request.getSharedWithUsername());
            share.setSharedWithIp(request.getSharedWithIp());
            share.setSharedWithOrganization(request.getSourceOrganization());
            share.setSameOrganization(false);
            share.setCreatedAt(request.getCreatedAt());
            share.setExpiresAt(request.getExpiresAt());
            share.setActive(true);
            
            videoShareRepository.save(share);
            log.info("Successfully synced share for video: {}", request.getVideoId());
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error syncing share for video: {}", request.getVideoId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 