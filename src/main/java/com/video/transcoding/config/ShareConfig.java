package com.video.transcoding.config;

import com.video.transcoding.model.Organization;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "video.share")
public class ShareConfig {
    private Map<String, String> endpoints = new HashMap<>();

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .build();
    }

    public String getEndpoint(Organization org) {
        return endpoints.getOrDefault(org.name(), null);
    }
} 