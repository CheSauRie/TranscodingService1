package com.video.transcoding.controller;

import com.video.transcoding.dto.TranscodingRequest;
import com.video.transcoding.service.VideoProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {
    private final VideoProcessingService videoProcessingService;
    private final KafkaTemplate<String, TranscodingRequest> kafkaTemplate;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String userId) {
        try {
            String videoId = UUID.randomUUID().toString();
            String originalFileName = file.getOriginalFilename();
            String extension = originalFileName.substring(originalFileName.lastIndexOf("."));

            // Save file temporarily
            Path tempDir = Path.of("temp");
            Files.createDirectories(tempDir);
            Path filePath = tempDir.resolve(videoId + extension);
            Files.copy(file.getInputStream(), filePath);

            // Create transcoding request
            TranscodingRequest request = new TranscodingRequest();
            request.setVideoId(videoId);
            request.setUserId(userId);
            request.setOriginalFileName(originalFileName);
            request.setExtension(extension);
            request.setOriginalFilePath(filePath.toString());

            // Send to Kafka for processing
            kafkaTemplate.send("video-transcoding", request);

            Map<String, String> response = new HashMap<>();
            response.put("videoId", videoId);
            response.put("message", "Video upload started, processing in background");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/{videoId}/url")
    public ResponseEntity<Map<String, String>> getVideoUrl(
            @PathVariable String videoId,
            @RequestParam String quality) {
        try {
            String url = videoProcessingService.getVideoUrl(videoId, quality);
            Map<String, String> response = new HashMap<>();
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
} 