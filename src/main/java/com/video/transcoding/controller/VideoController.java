package com.video.transcoding.controller;

import com.video.transcoding.dto.TranscodingRequest;
import com.video.transcoding.service.VideoProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
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
            // Lấy URL video từ MinIO
            String url = videoProcessingService.getVideoUrl(videoId, quality);
            Map<String, String> response = new HashMap<>();
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/share")
    public ResponseEntity<Map<String, String>> shareVideo(
            @PathVariable String videoId,
            @RequestBody ShareRequest request) {
        // Kiểm tra quyền chia sẻ
        // Tạo VideoShare record
        // Gửi thông báo cho người nhận
        // Trả về URL chia sẻ
        return null; // Placeholder return, actual implementation needed
    }

    @KafkaListener(topics = "video-transcoding-result")
    public void handleTranscodingResult(TranscodingResult result) {
        if (result.isSuccess()) {
            // Gửi email thông báo
            notificationService.sendEmail(
                result.getUserId(),
                "Video Processing Complete",
                "Your video " + result.getVideoId() + " has been processed successfully."
            );
            
            // Hoặc gửi thông báo WebSocket
            notificationService.sendWebSocketNotification(
                result.getUserId(),
                "Video " + result.getVideoId() + " is ready!"
            );
        }
    }
} 