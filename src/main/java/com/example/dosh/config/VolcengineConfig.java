package com.example.dosh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "volcengine")
@Data
public class VolcengineConfig {
    private String apiKey;
    private String apiUrl;
    private String model;
}
