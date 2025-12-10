package com.example.dosh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "comfyui")
@Data
public class ComfyUIConfig {
    private String serverAddress;
}
