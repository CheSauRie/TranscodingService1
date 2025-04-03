package com.video.transcoding.service;

import com.video.transcoding.dto.VideoProgressMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendProgress(String userId, String videoId, String status, int progress) {
        VideoProgressMessage message = new VideoProgressMessage();
        message.setVideoId(videoId);
        message.setStatus(status);
        message.setProgress(progress);
        message.setTimestamp(java.time.LocalDateTime.now());
        
        messagingTemplate.convertAndSendToUser(
            userId,
            "/topic/video-progress",
            message
        );
    }
} 