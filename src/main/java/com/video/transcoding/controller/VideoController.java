package com.video.transcoding.controller;

import com.video.transcoding.dto.TranscodingRequest;
import com.video.transcoding.service.VideoProcessingService;
import com.video.transcoding.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {
    private final VideoProcessingService videoProcessingService;
    private final KafkaTemplate<String, TranscodingRequest> kafkaTemplate;
    private final WebSocketService webSocketService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String userId) {
        try {
            String videoId = UUID.randomUUID().toString();
            String originalFileName = file.getOriginalFilename();
            String extension = originalFileName.substring(originalFileName.lastIndexOf("."));

            // Gửi thông báo bắt đầu upload
            webSocketService.sendProgress(userId, videoId, "UPLOADING", 0);

            // Lưu file tạm thời
            Path tempDir = Path.of("temp");
            Files.createDirectories(tempDir);
            Path filePath = tempDir.resolve(videoId + extension);
            
            // Upload với progress tracking
            try (var inputStream = file.getInputStream()) {
                long totalBytes = file.getSize();
                long bytesRead = 0;
                byte[] buffer = new byte[8192];
                int read;
                
                while ((read = inputStream.read(buffer)) != -1) {
                    Files.write(filePath, buffer, 0, read, StandardOpenOption.APPEND);
                    bytesRead += read;
                    int progress = (int) ((bytesRead * 100) / totalBytes);
                    webSocketService.sendProgress(userId, videoId, "UPLOADING", progress);
                }
            }

            // Gửi thông báo upload hoàn thành
            webSocketService.sendProgress(userId, videoId, "UPLOAD_COMPLETED", 100);

            // Tạo request cho Kafka
            TranscodingRequest request = new TranscodingRequest();
            request.setVideoId(videoId);
            request.setUserId(userId);
            request.setOriginalFileName(originalFileName);
            request.setExtension(extension);
            request.setOriginalFilePath(filePath.toString());

            // Gửi request vào Kafka topic
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