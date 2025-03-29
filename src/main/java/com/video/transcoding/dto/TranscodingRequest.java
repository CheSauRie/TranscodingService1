package com.video.transcoding.dto;

import lombok.Data;

@Data
public class TranscodingRequest {
    private String videoId;
    private String userId;
    private String originalFileName;
    private String extension;
    private String originalFilePath;
} 