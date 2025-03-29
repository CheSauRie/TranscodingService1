package com.video.transcoding.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "videos")
public class Video {
    @Id
    private String id;
    private String userId;
    private String originalFileName;
    private String extension;
    private List<VideoQuality> qualities;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class VideoQuality {
        private String name;
        private int height;
        private String bitrate;
        private String preset;
        private int crf;
        private String objectName;
    }
} 