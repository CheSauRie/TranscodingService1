package com.video.transcoding.service;

import com.video.transcoding.config.VideoProcessingConfig;
import com.video.transcoding.dto.TranscodingRequest;
import com.video.transcoding.dto.TranscodingResult;
import com.video.transcoding.model.Video;
import com.video.transcoding.repository.VideoRepository;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodingService {
    private final MinioClient minioClient;
    private final VideoProcessingConfig videoProcessingConfig;
    private final VideoRepository videoRepository;
    private final KafkaTemplate<String, TranscodingResult> kafkaTemplate;

    public void processVideo(TranscodingRequest request) {
        try {
            // Create temp directory if it doesn't exist
            Path tempDir = Path.of(videoProcessingConfig.getTempDir());
            Files.createDirectories(tempDir);

            // Save uploaded file temporarily
            Path originalVideoPath = tempDir.resolve(request.getVideoId() + request.getExtension());
            Files.copy(Path.of(request.getOriginalFilePath()), originalVideoPath, StandardCopyOption.REPLACE_EXISTING);

            // Create video document
            Video video = new Video();
            video.setId(request.getVideoId());
            video.setUserId(request.getUserId());
            video.setOriginalFileName(request.getOriginalFileName());
            video.setExtension(request.getExtension());
            video.setCreatedAt(LocalDateTime.now());
            video.setUpdatedAt(LocalDateTime.now());

            // Process video for different qualities
            List<String> processedFiles = new ArrayList<>();
            List<Video.VideoQuality> qualities = new ArrayList<>();

            for (VideoProcessingConfig.Quality quality : videoProcessingConfig.getQualities()) {
                String outputFileName = request.getVideoId() + "_" + quality.getName() + request.getExtension();
                Path outputPath = tempDir.resolve(outputFileName);
                
                // Transcode video using FFmpeg
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
                String objectName = request.getVideoId() + "/" + outputFileName;
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

            // Send success result
            TranscodingResult result = new TranscodingResult();
            result.setVideoId(request.getVideoId());
            result.setUserId(request.getUserId());
            result.setSuccess(true);
            result.setCompletedAt(LocalDateTime.now());
            result.setQualities(convertToResultQualities(qualities));
            kafkaTemplate.send("video-transcoding-result", result);

        } catch (Exception e) {
            log.error("Error processing video: {}", request.getVideoId(), e);
            
            // Send error result
            TranscodingResult result = new TranscodingResult();
            result.setVideoId(request.getVideoId());
            result.setUserId(request.getUserId());
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setCompletedAt(LocalDateTime.now());
            kafkaTemplate.send("video-transcoding-result", result);
        }
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

    private List<TranscodingResult.VideoQuality> convertToResultQualities(List<Video.VideoQuality> qualities) {
        List<TranscodingResult.VideoQuality> result = new ArrayList<>();
        for (Video.VideoQuality quality : qualities) {
            TranscodingResult.VideoQuality resultQuality = new TranscodingResult.VideoQuality();
            resultQuality.setName(quality.getName());
            resultQuality.setHeight(quality.getHeight());
            resultQuality.setBitrate(quality.getBitrate());
            resultQuality.setObjectName(quality.getObjectName());
            result.add(resultQuality);
        }
        return result;
    }
} 