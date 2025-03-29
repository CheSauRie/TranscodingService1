package com.video.transcoding.service;

import com.video.transcoding.config.ShareConfig;
import com.video.transcoding.dto.ShareSyncRequest;
import com.video.transcoding.model.Organization;
import com.video.transcoding.model.Video;
import com.video.transcoding.model.VideoShare;
import com.video.transcoding.repository.VideoRepository;
import com.video.transcoding.repository.VideoShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoShareService {
    private final VideoRepository videoRepository;
    private final VideoShareRepository videoShareRepository;
    private final ShareConfig shareConfig;
    private final WebClient webClient;
    private final Organization currentOrg;

    public VideoShare shareVideo(String videoId, String sharedByUserId, String sharedWithUsername, String sharedWithIp) {
        // Check if video exists and belongs to the user
        Video video = videoRepository.findById(videoId)
            .orElseThrow(() -> new RuntimeException("Video not found"));

        if (!video.getUserId().equals(sharedByUserId)) {
            throw new RuntimeException("You don't have permission to share this video");
        }

        // Check if already shared with this user
        if (videoShareRepository.existsByVideoIdAndSharedWithUsernameAndIsActiveTrue(videoId, sharedWithUsername)) {
            throw new RuntimeException("Video already shared with this user");
        }

        // Get organization from IP
        Organization sharedWithOrg = Organization.fromIp(sharedWithIp);
        
        // Create share record
        VideoShare share = new VideoShare();
        share.setId(UUID.randomUUID().toString());
        share.setVideoId(videoId);
        share.setSharedByUserId(sharedByUserId);
        share.setSharedWithUsername(sharedWithUsername);
        share.setSharedWithIp(sharedWithIp);
        share.setSharedWithOrganization(sharedWithOrg);
        share.setSameOrganization(currentOrg == sharedWithOrg);
        share.setCreatedAt(LocalDateTime.now());
        share.setExpiresAt(LocalDateTime.now().plusDays(7)); // Share expires in 7 days
        share.setActive(true);

        // Save share record
        share = videoShareRepository.save(share);

        // If sharing with different organization, sync with target organization
        if (!share.isSameOrganization()) {
            syncWithTargetOrganization(share);
        }

        return share;
    }

    private void syncWithTargetOrganization(VideoShare share) {
        String targetEndpoint = shareConfig.getEndpoint(share.getSharedWithOrganization());
        if (targetEndpoint == null) {
            log.error("No endpoint configured for organization: {}", share.getSharedWithOrganization());
            return;
        }

        ShareSyncRequest request = new ShareSyncRequest();
        request.setVideoId(share.getVideoId());
        request.setSharedByUserId(share.getSharedByUserId());
        request.setSharedWithUsername(share.getSharedWithUsername());
        request.setSharedWithIp(share.getSharedWithIp());
        request.setSourceOrganization(currentOrg);
        request.setCreatedAt(share.getCreatedAt());
        request.setExpiresAt(share.getExpiresAt());

        webClient.post()
                .uri(targetEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Successfully synced share with organization: {}", share.getSharedWithOrganization()))
                .doOnError(e -> log.error("Error syncing share with organization: {}", share.getSharedWithOrganization(), e))
                .subscribe();
    }

    public List<VideoShare> getSharedVideos(String username) {
        return videoShareRepository.findBySharedWithUsernameAndIsActiveTrue(username);
    }

    public List<VideoShare> getVideoShares(String videoId) {
        return videoShareRepository.findByVideoIdAndIsActiveTrue(videoId);
    }

    public void revokeShare(String shareId, String userId) {
        VideoShare share = videoShareRepository.findById(shareId)
            .orElseThrow(() -> new RuntimeException("Share not found"));

        Video video = videoRepository.findById(share.getVideoId())
            .orElseThrow(() -> new RuntimeException("Video not found"));

        if (!video.getUserId().equals(userId)) {
            throw new RuntimeException("You don't have permission to revoke this share");
        }

        share.setActive(false);
        videoShareRepository.save(share);

        // If share was with different organization, revoke in target organization
        if (!share.isSameOrganization()) {
            revokeInTargetOrganization(share);
        }
    }

    private void revokeInTargetOrganization(VideoShare share) {
        String targetEndpoint = shareConfig.getEndpoint(share.getSharedWithOrganization());
        if (targetEndpoint == null) {
            log.error("No endpoint configured for organization: {}", share.getSharedWithOrganization());
            return;
        }

        ShareSyncRequest request = new ShareSyncRequest();
        request.setVideoId(share.getVideoId());
        request.setSharedWithUsername(share.getSharedWithUsername());

        webClient.post()
                .uri(targetEndpoint + "/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Successfully revoked share in organization: {}", share.getSharedWithOrganization()))
                .doOnError(e -> log.error("Error revoking share in organization: {}", share.getSharedWithOrganization(), e))
                .subscribe();
    }
} 