package com.video.transcoding.service;

import com.video.transcoding.config.ShareConfig;
import com.video.transcoding.config.VideoProcessingConfig;
import com.video.transcoding.dto.ShareSyncRequest;
import com.video.transcoding.dto.VideoSyncRequest;
import com.video.transcoding.model.Organization;
import com.video.transcoding.model.Video;
import com.video.transcoding.model.VideoShare;
import com.video.transcoding.repository.VideoRepository;
import com.video.transcoding.repository.VideoShareRepository;
import io.minio.*;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import org.springframework.web.reactive.function.client.WebClient.MultipartBodyBuilder;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoShareService {
    private final VideoRepository videoRepository;
    private final VideoShareRepository videoShareRepository;
    private final MinioClient minioClient;
    private final ShareConfig shareConfig;
    private final VideoProcessingConfig videoProcessingConfig;
    private final WebClient webClient;
    private final MinioClient minioClient;
    private final Organization currentOrg;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Value("${minio.bucket}")
    private String bucketName;

    public VideoShare shareVideo(String videoId, String sharedWithUsername, String sharedWithIp, String sharedWithOrganization) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("Video not found"));

        VideoShare share = new VideoShare();
        share.setVideoId(videoId);
        share.setSharedByUserId(video.getUserId());
        share.setSharedWithUsername(sharedWithUsername);
        share.setSharedWithIp(sharedWithIp);
        share.setSharedWithOrganization(sharedWithOrganization);
        share.setCreatedAt(LocalDateTime.now());
        share.setExpiresAt(LocalDateTime.now().plusDays(30)); // 30 days expiry

        VideoShare savedShare = videoShareRepository.save(share);
        
        // Sync with target organization
        syncWithTargetOrganization(savedShare, video);
        
        return savedShare;
    }

    private void syncWithTargetOrganization(VideoShare share, Video video) {
    private void syncWithTargetOrganization(VideoShare share, Video video) {
        String targetEndpoint = shareConfig.getEndpoint(share.getSharedWithOrganization());
        if (targetEndpoint == null) {
            log.error("No endpoint configured for organization: {}", share.getSharedWithOrganization());
            return;
        }

        // Create sync request with video file information
        VideoSyncRequest request = new VideoSyncRequest();
        request.setVideoId(share.getVideoId());
        request.setSharedByUserId(share.getSharedByUserId());
        request.setSharedWithUsername(share.getSharedWithUsername());
        request.setSharedWithIp(share.getSharedWithIp());
        request.setSourceOrganization(currentOrg.getName());
        request.setCreatedAt(share.getCreatedAt());
        request.setExpiresAt(share.getExpiresAt());
        
        // Add video qualities information
        request.setQualities(video.getQualities().stream()
                .map(quality -> {
                    VideoSyncRequest.VideoQuality syncQuality = new VideoSyncRequest.VideoQuality();
                    syncQuality.setName(quality.getName());
                    syncQuality.setObjectName(quality.getObjectName());
                    syncQuality.setContentType("video/mp4");
                    try {
                        // Get object size from MinIO
                        StatObjectResponse stat = minioClient.statObject(
                            StatObjectArgs.builder()
                                .bucket(bucketName)
                                .object(quality.getObjectName())
                                .build()
                        );
                        syncQuality.setSize(stat.size());
                    } catch (Exception e) {
                        log.error("Error getting object size for {}: {}", quality.getObjectName(), e.getMessage());
                    }
                    return syncQuality;
                })
                .collect(Collectors.toList()));

        // Send sync request
        webClient.post()
                .uri(targetEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> {
                    log.info("Successfully synced share with organization: {}", share.getSharedWithOrganization());
                    // After successful sync, transfer video files
                    transferVideoFiles(video, share.getSharedWithOrganization());
                })
                .doOnError(e -> log.error("Error syncing share with organization: {}", share.getSharedWithOrganization(), e))
                .subscribe();
    }

    private void transferVideoFiles(Video video, String targetOrganization) {
        String targetEndpoint = shareConfig.getEndpoint(targetOrganization);
        if (targetEndpoint == null) {
            log.error("No endpoint configured for organization: {}", targetOrganization);
            return;
        }

        // Transfer each video quality
        for (Video.VideoQuality quality : video.getQualities()) {
            try {
                // Get object from source MinIO
                InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(quality.getObjectName())
                        .build()
                );

                // Upload to target MinIO
                webClient.post()
                    .uri(targetEndpoint + "/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(new MultipartBodyBuilder()
                        .part("file", inputStream)
                        .part("objectName", quality.getObjectName())
                        .build())
                    .retrieve()
                    .bodyToMono(Void.class)
                    .doOnSuccess(v -> log.info("Successfully transferred video file: {}", quality.getObjectName()))
                    .doOnError(e -> log.error("Error transferring video file: {}", quality.getObjectName(), e))
                    .subscribe();

            } catch (Exception e) {
                log.error("Error transferring video file {}: {}", quality.getObjectName(), e.getMessage());
            }
        }
    }

    public List<VideoShare> getSharedVideos(String userId) {
        return videoShareRepository.findBySharedByUserId(userId);
    }

    public List<VideoShare> getReceivedVideos(String username, String ip) {
        return videoShareRepository.findBySharedWithUsernameAndSharedWithIp(username, ip);
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
            executorService.submit(() -> revokeInTargetOrganization(share));
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
                .block(); // Block to ensure completion
    }
}