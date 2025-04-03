package com.video.transcoding.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VideoProgressMessage {
    private String videoId;
    private String status;
    private int progress;
    private LocalDateTime timestamp;
} 