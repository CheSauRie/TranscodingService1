package com.video.transcoding.repository;

import com.video.transcoding.model.Organization;
import com.video.transcoding.model.ShareSync;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareSyncRepository extends MongoRepository<ShareSync, String> {
    List<ShareSync> findByTargetOrganizationAndIsProcessedFalse(Organization targetOrg);
    List<ShareSync> findBySourceOrganizationAndStatus(Organization sourceOrg, String status);
} 