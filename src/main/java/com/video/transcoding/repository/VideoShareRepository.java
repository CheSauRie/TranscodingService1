package com.video.transcoding.repository;

import com.video.transcoding.model.VideoShare;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoShareRepository extends MongoRepository<VideoShare, String> {
    List<VideoShare> findByVideoIdAndIsActiveTrue(String videoId);
    List<VideoShare> findBySharedWithUsernameAndIsActiveTrue(String username);
    boolean existsByVideoIdAndSharedWithUsernameAndIsActiveTrue(String videoId, String username);
} 