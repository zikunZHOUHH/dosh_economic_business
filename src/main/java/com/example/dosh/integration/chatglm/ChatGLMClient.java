package com.example.dosh.integration.chatglm;

import com.example.dosh.config.ChatGLMConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ChatGLMClient {

    private final ChatGLMConfig chatGLMConfig;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public ChatGLMClient(ChatGLMConfig chatGLMConfig, ObjectMapper mapper) {
        this.chatGLMConfig = chatGLMConfig;
        this.mapper = mapper;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void streamGenerate(String prompt, SseEmitter emitter) {
        log.info("Calling ChatGLM API (Stream) with prompt: {}", prompt);
        
        try {
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> requestBodyMap = new HashMap<>();
            requestBodyMap.put("model", chatGLMConfig.getModel());
            requestBodyMap.put("messages", Collections.singletonList(message));
            requestBodyMap.put("temperature", 0.6);
            requestBodyMap.put("stream", true); // Enable streaming

            String jsonBody = mapper.writeValueAsString(requestBodyMap);
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, jsonBody);

            Request request = new Request.Builder()
                    .url(chatGLMConfig.getApiUrl())
                    .addHeader("Authorization", "Bearer " + chatGLMConfig.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(request, new EventSourceListener() {
                @Override
                public void onOpen(EventSource eventSource, Response response) {
                    log.info("ChatGLM Stream Opened");
                }

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    log.debug("ChatGLM Stream Event: {}", data);
                    if ("[DONE]".equals(data)) {
                        try {
                            emitter.complete();
                        } catch (Exception e) {
                            log.error("Error completing emitter", e);
                        }
                        return;
                    }

                    try {
                        JsonNode rootNode = mapper.readTree(data);
                        if (rootNode.has("choices") && rootNode.get("choices").isArray() && rootNode.get("choices").size() > 0) {
                            JsonNode deltaNode = rootNode.get("choices").get(0).path("delta");
                            if (deltaNode.has("content")) {
                                String content = deltaNode.get("content").asText();
                                emitter.send(SseEmitter.event().data(content));
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error parsing stream data", e);
                    }
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    log.info("ChatGLM Stream Closed");
                    emitter.complete();
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    log.error("ChatGLM Stream Failed", t);
                    emitter.completeWithError(t);
                }
            });

        } catch (Exception e) {
            log.error("Error initiating ChatGLM stream", e);
            emitter.completeWithError(e);
        }
    }

    public String generate(String prompt) {
        log.info("Calling ChatGLM API with prompt: {}", prompt);
        
        try {
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> requestBodyMap = new HashMap<>();
            requestBodyMap.put("model", chatGLMConfig.getModel());
            requestBodyMap.put("messages", Collections.singletonList(message));
            requestBodyMap.put("temperature", 0.6);

            String jsonBody = mapper.writeValueAsString(requestBodyMap);
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, jsonBody);

            Request request = new Request.Builder()
                    .url(chatGLMConfig.getApiUrl())
                    .addHeader("Authorization", "Bearer " + chatGLMConfig.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("ChatGLM API call failed with code: {} and body: {}", 
                            response.code(), response.body() != null ? response.body().string() : "null");
                    throw new IOException("Unexpected code " + response);
                }

                String responseBody = response.body().string();
                log.debug("ChatGLM raw response: {}", responseBody);
                
                // Parse response to extract content
                JsonNode rootNode = mapper.readTree(responseBody);
                if (rootNode.has("choices") && rootNode.get("choices").isArray() && rootNode.get("choices").size() > 0) {
                     JsonNode contentNode = rootNode.get("choices").get(0).path("message").path("content");
                     return contentNode.asText();
                }
                
                return responseBody;
            }

        } catch (Exception e) {
            log.error("Error calling ChatGLM API", e);
            throw new RuntimeException("Failed to call ChatGLM API", e);
        }
    }
}
