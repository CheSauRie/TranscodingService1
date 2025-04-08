package com.video.transcoding.controller;

import com.video.transcoding.dto.VideoSyncRequest;
import com.video.transcoding.model.Video;
import com.video.transcoding.model.VideoShare;
import com.video.transcoding.repository.VideoRepository;
import com.video.transcoding.repository.VideoShareRepository;
import com.video.transcoding.service.WebSocketService;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/videos/sync")
@RequiredArgsConstructor
public class VideoSyncController {
    private final VideoRepository videoRepository;
    private final VideoShareRepository videoShareRepository;
    private final MinioClient minioClient;
    private final WebSocketService webSocketService;

    @Value("${minio.bucket}")
    private String bucketName;

    @PostMapping
    public ResponseEntity<Void> syncVideo(@RequestBody VideoSyncRequest request) {
        try {
            // Create video document
            Video video = new Video();
            video.setId(request.getVideoId());
            video.setUserId(request.getSharedByUserId());
            video.setCreatedAt(request.getCreatedAt());
            video.setUpdatedAt(LocalDateTime.now());

            // Convert qualities
            List<Video.VideoQuality> qualities = request.getQualities().stream()
                .map(quality -> {
                    Video.VideoQuality videoQuality = new Video.VideoQuality();
                    videoQuality.setName(quality.getName());
                    videoQuality.setObjectName(quality.getObjectName());
                    return videoQuality;
                })
                .collect(java.util.stream.Collectors.toList());
            video.setQualities(qualities);

            // Save video
            videoRepository.save(video);

            // Create share record
            VideoShare share = new VideoShare();
            share.setVideoId(request.getVideoId());
            share.setSharedByUserId(request.getSharedByUserId());
            share.setSharedWithUsername(request.getSharedWithUsername());
            share.setSharedWithIp(request.getSharedWithIp());
            share.setSharedWithOrganization(request.getSourceOrganization());
            share.setCreatedAt(request.getCreatedAt());
            share.setExpiresAt(request.getExpiresAt());
            videoShareRepository.save(share);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error syncing video: {}", request.getVideoId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadVideoFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("objectName") String objectName) {
        try {
            // Upload to MinIO
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
                );
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error uploading video file: {}", objectName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 