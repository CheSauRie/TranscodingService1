package com.video.transcoding.service;

import com.video.transcoding.config.ShareConfig;
import com.video.transcoding.config.VideoProcessingConfig;
import com.video.transcoding.dto.ShareSyncRequest;
import com.video.transcoding.model.Organization;
import com.video.transcoding.model.Video;
import com.video.transcoding.model.VideoShare;
import com.video.transcoding.repository.VideoRepository;
import com.video.transcoding.repository.VideoShareRepository;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoShareService {
    private final VideoRepository videoRepository;
    private final VideoShareRepository videoShareRepository;
    private final ShareConfig shareConfig;
    private final VideoProcessingConfig videoProcessingConfig;
    private final WebClient webClient;
    private final MinioClient minioClient;
    private final Organization currentOrg;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

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
            // Execute synchronization in a separate thread
            executorService.submit(() -> {
                try {
                    syncWithTargetOrganization(share, video);
                } catch (Exception e) {
                    log.error("Error during video share synchronization", e);
                }
            });
        }

        return share;
    }

    private void syncWithTargetOrganization(VideoShare share, Video video) {
        String targetEndpoint = shareConfig.getEndpoint(share.getSharedWithOrganization());
        if (targetEndpoint == null) {
            log.error("No endpoint configured for organization: {}", share.getSharedWithOrganization());
            return;
        }

        // First, copy all video files to target MinIO
        try {
            log.info("Starting to copy video files for sharing: {}", video.getId());
            boolean filesCopied = copyVideoFilesToTargetSite(video, share.getSharedWithOrganization());
            
            if (!filesCopied) {
                log.error("Failed to copy video files to target site for video: {}", video.getId());
                return;
            }
            log.info("Successfully copied all video files to target site for video: {}", video.getId());
            
            // Now create the share record on the target site
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
                    .block(); // Block to ensure completion before continuing
            
        } catch (Exception e) {
            log.error("Error synchronizing video share", e);
        }
    }
    
    private boolean copyVideoFilesToTargetSite(Video video, Organization targetOrg) {
        try {
            Path tempDir = Files.createTempDirectory("video_share_" + video.getId());
            boolean success = true;
            
            // Copy each quality version
            for (Video.VideoQuality quality : video.getQualities()) {
                String objectName = quality.getObjectName();
                
                // Step 1: Download from our MinIO
                Path localFile = tempDir.resolve(objectName.substring(objectName.lastIndexOf('/') + 1));
                try {
                    downloadFileFromMinio(objectName, localFile);
                } catch (Exception e) {
                    log.error("Failed to download video file from MinIO: {}", objectName, e);
                    success = false;
                    continue;
                }
                
                // Step 2: Upload to target site MinIO via API
                try {
                    uploadFileToTargetSite(localFile, objectName, targetOrg);
                } catch (Exception e) {
                    log.error("Failed to upload video file to target site: {}", objectName, e);
                    success = false;
                }
                
                // Clean up local file
                try {
                    Files.deleteIfExists(localFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file: {}", localFile, e);
                }
            }
            
            // Clean up temp directory
            try {
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                log.warn("Failed to delete temporary directory: {}", tempDir, e);
            }
            
            return success;
        } catch (Exception e) {
            log.error("Error during video file copy process", e);
            return false;
        }
    }
    
    private void downloadFileFromMinio(String objectName, Path destination) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(videoProcessingConfig.getTempDir())
                    .object(objectName)
                    .build())) {
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    private void uploadFileToTargetSite(Path sourceFile, String objectName, Organization targetOrg) throws Exception {
        String targetEndpoint = shareConfig.getEndpoint(targetOrg).replace("/api/videos/share/sync", "/api/videos/share/upload-file");
        
        // Create a multipart form data request to upload the file
        webClient.post()
                .uri(targetEndpoint)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(createMultipartData(sourceFile, objectName))
                .retrieve()
                .bodyToMono(String.class)
                .block(); // Block to ensure completion
    }
    
    private org.springframework.http.codec.multipart.MultipartBodyBuilder createMultipartData(Path file, String objectName) throws IOException {
        org.springframework.http.codec.multipart.MultipartBodyBuilder builder = new org.springframework.http.codec.multipart.MultipartBodyBuilder();
        builder.part("file", Files.readAllBytes(file));
        builder.part("objectName", objectName);
        return builder.build();
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