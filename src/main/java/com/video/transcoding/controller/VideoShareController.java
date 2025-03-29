package com.video.transcoding.controller;

import com.video.transcoding.model.Organization;
import com.video.transcoding.model.VideoShare;
import com.video.transcoding.service.VideoShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos/share")
@RequiredArgsConstructor
public class VideoShareController {
    private final VideoShareService videoShareService;

    @PostMapping("/{videoId}")
    public ResponseEntity<Map<String, Object>> shareVideo(
            @PathVariable String videoId,
            @RequestParam String sharedWithUsername,
            @RequestParam String sharedWithIp,
            @AuthenticationPrincipal String userId) {
        try {
            // Validate IP belongs to known organizations
            Organization.fromIp(sharedWithIp);
            
            VideoShare share = videoShareService.shareVideo(videoId, userId, sharedWithUsername, sharedWithIp);
            
            Map<String, Object> response = new HashMap<>();
            response.put("shareId", share.getId());
            response.put("sharedWithOrganization", share.getSharedWithOrganization());
            response.put("isSameOrganization", share.isSameOrganization());
            response.put("expiresAt", share.getExpiresAt());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid organization IP. Must be either 192.168.205.108 or 192.168.205.104");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<List<VideoShare>> getSharedVideos(
            @RequestParam String username) {
        return ResponseEntity.ok(videoShareService.getSharedVideos(username));
    }

    @GetMapping("/{videoId}/shares")
    public ResponseEntity<List<VideoShare>> getVideoShares(
            @PathVariable String videoId) {
        return ResponseEntity.ok(videoShareService.getVideoShares(videoId));
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revokeShare(
            @PathVariable String shareId,
            @AuthenticationPrincipal String userId) {
        try {
            videoShareService.revokeShare(shareId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
} 