package com.video.transcoding.service;

import com.video.transcoding.config.VideoProcessingConfig;
import com.video.transcoding.model.Video;
import com.video.transcoding.repository.VideoRepository;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {
    private final MinioClient minioClient;
    private final VideoProcessingConfig videoProcessingConfig;
    private final VideoRepository videoRepository;

    public String processVideo(MultipartFile file, String userId) throws Exception {
        String videoId = UUID.randomUUID().toString();
        String originalFileName = file.getOriginalFilename();
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));

        // Create temp directory if it doesn't exist
        Path tempDir = Path.of(videoProcessingConfig.getTempDir());
        Files.createDirectories(tempDir);

        // Save uploaded file temporarily
        Path originalVideoPath = tempDir.resolve(videoId + extension);
        Files.copy(file.getInputStream(), originalVideoPath, StandardCopyOption.REPLACE_EXISTING);

        // Create video document
        Video video = new Video();
        video.setId(videoId);
        video.setUserId(userId);
        video.setOriginalFileName(originalFileName);
        video.setExtension(extension);
        video.setCreatedAt(LocalDateTime.now());
        video.setUpdatedAt(LocalDateTime.now());

        // Process video for different qualities
        List<String> processedFiles = new ArrayList<>();
        List<Video.VideoQuality> qualities = new ArrayList<>();

        for (VideoProcessingConfig.Quality quality : videoProcessingConfig.getQualities()) {
            String outputFileName = videoId + "_" + quality.getName() + extension;
            Path outputPath = tempDir.resolve(outputFileName);
            
            // Transcode video using FFmpeg with improved quality settings
            String ffmpegCommand = String.format(
                "ffmpeg -i %s -vf scale=-2:%d -c:v libx264 -preset %s -crf %d -b:v %s " +
                "-c:a aac -b:a 192k -ar 48000 -ac 2 -movflags +faststart %s",
                originalVideoPath,
                quality.getHeight(),
                quality.getPreset(),
                quality.getCrf(),
                quality.getBitrate(),
                outputPath
            );
            
            log.info("Executing FFmpeg command: {}", ffmpegCommand);
            Process process = Runtime.getRuntime().exec(ffmpegCommand);
            process.waitFor();

            // Upload to MinIO
            String objectName = videoId + "/" + outputFileName;
            uploadToMinio(outputPath.toFile(), objectName);
            processedFiles.add(outputPath.toString());

            // Create video quality info
            Video.VideoQuality videoQuality = new Video.VideoQuality();
            videoQuality.setName(quality.getName());
            videoQuality.setHeight(quality.getHeight());
            videoQuality.setBitrate(quality.getBitrate());
            videoQuality.setPreset(quality.getPreset());
            videoQuality.setCrf(quality.getCrf());
            videoQuality.setObjectName(objectName);
            qualities.add(videoQuality);
        }

        video.setQualities(qualities);
        videoRepository.save(video);

        // Clean up temporary files
        Files.delete(originalVideoPath);
        for (String processedFile : processedFiles) {
            Files.delete(Path.of(processedFile));
        }

        return videoId;
    }

    private void uploadToMinio(File file, String objectName) throws Exception {
        try (InputStream inputStream = new FileInputStream(file)) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(videoProcessingConfig.getTempDir())
                    .object(objectName)
                    .stream(inputStream, file.length(), -1)
                    .contentType("video/mp4")
                    .build()
            );
        }
    }

    public String getVideoUrl(String videoId, String quality) throws Exception {
        Video video = videoRepository.findById(videoId)
            .orElseThrow(() -> new RuntimeException("Video not found"));

        String objectName = video.getQualities().stream()
            .filter(q -> q.getName().equals(quality))
            .map(Video.VideoQuality::getObjectName)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Quality not found"));

        return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .bucket(videoProcessingConfig.getTempDir())
                .object(objectName)
                .method(Method.GET)
                .expiry(7, TimeUnit.DAYS)
                .build()
        );
    }
} 