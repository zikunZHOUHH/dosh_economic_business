package com.example.dosh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

@Configuration
@Getter
public class IntentConfig {

    @Value("${ai.intent.url:http://localhost:8000/predict}")
    private String intentUrl;

    @Value("${ai.intent.provider:chatglm}")
    private String provider; // "chatglm" or "local"
}
