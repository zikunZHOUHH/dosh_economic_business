package com.example.dosh.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class ChatGLMConfig {

    @Value("${chatglm.api.key}")
    private String apiKey;

    @Value("${chatglm.api.url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String apiUrl;

    @Value("${chatglm.model:glm-4.5}")
    private String model;
}
