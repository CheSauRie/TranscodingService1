package com.video.transcoding.service;

import com.video.transcoding.model.Organization;
import com.video.transcoding.model.ShareSync;
import com.video.transcoding.model.VideoShare;
import com.video.transcoding.repository.ShareSyncRepository;
import com.video.transcoding.repository.VideoShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareSyncService {
    private final ShareSyncRepository shareSyncRepository;
    private final VideoShareRepository videoShareRepository;
    private final Organization currentOrg;

    public ShareSync createShareSync(VideoShare share) {
        ShareSync sync = new ShareSync();
        sync.setId(UUID.randomUUID().toString());
        sync.setVideoId(share.getVideoId());
        sync.setSharedByUserId(share.getSharedByUserId());
        sync.setSharedWithUsername(share.getSharedWithUsername());
        sync.setSharedWithIp(share.getSharedWithIp());
        sync.setSourceOrganization(currentOrg);
        sync.setTargetOrganization(share.getSharedWithOrganization());
        sync.setCreatedAt(LocalDateTime.now());
        sync.setExpiresAt(share.getExpiresAt());
        sync.setIsProcessed(false);
        sync.setStatus("PENDING");
        
        return shareSyncRepository.save(sync);
    }

    @Scheduled(fixedRate = 60000) // Run every minute
    public void processPendingShares() {
        List<ShareSync> pendingShares = shareSyncRepository.findByTargetOrganizationAndIsProcessedFalse(currentOrg);
        
        for (ShareSync sync : pendingShares) {
            try {
                // Create VideoShare in current organization
                VideoShare share = new VideoShare();
                share.setId(UUID.randomUUID().toString());
                share.setVideoId(sync.getVideoId());
                share.setSharedByUserId(sync.getSharedByUserId());
                share.setSharedWithUsername(sync.getSharedWithUsername());
                share.setSharedWithIp(sync.getSharedWithIp());
                share.setSharedWithOrganization(sync.getSourceOrganization());
                share.setSameOrganization(false);
                share.setCreatedAt(sync.getCreatedAt());
                share.setExpiresAt(sync.getExpiresAt());
                share.setActive(true);
                
                videoShareRepository.save(share);
                
                // Mark sync as processed
                sync.setIsProcessed(true);
                sync.setStatus("PROCESSED");
                shareSyncRepository.save(sync);
                
                log.info("Successfully processed share sync: {}", sync.getId());
            } catch (Exception e) {
                log.error("Error processing share sync: {}", sync.getId(), e);
                sync.setStatus("FAILED");
                sync.setErrorMessage(e.getMessage());
                shareSyncRepository.save(sync);
            }
        }
    }

    public void revokeShareSync(String videoId) {
        List<ShareSync> syncs = shareSyncRepository.findBySourceOrganizationAndStatus(currentOrg, "PENDING");
        for (ShareSync sync : syncs) {
            if (sync.getVideoId().equals(videoId)) {
                sync.setStatus("FAILED");
                sync.setErrorMessage("Share revoked");
                shareSyncRepository.save(sync);
            }
        }
    }
} 