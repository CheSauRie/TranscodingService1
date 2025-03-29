package com.video.transcoding.service;

import com.video.transcoding.dto.TranscodingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodingConsumer {
    private final TranscodingService transcodingService;

    @KafkaListener(topics = "video-transcoding", groupId = "video-transcoding-group")
    public void consume(TranscodingRequest request) {
        log.info("Received transcoding request for video: {}", request.getVideoId());
        transcodingService.processVideo(request);
    }
} 