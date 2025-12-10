package com.example.dosh.integration.intent;

import com.example.dosh.config.IntentConfig;
import com.example.dosh.integration.chatglm.ChatGLMClient;
import com.example.dosh.model.dto.intent.IntentRequest;
import com.example.dosh.model.dto.intent.IntentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class IntentClient {

    private final RestTemplate restTemplate;
    private final IntentConfig intentConfig;
    private final ChatGLMClient chatGLMClient;
    private final ObjectMapper objectMapper;

    /**
     * Call the intent recognition model (Local or LLM).
     *
     * @param text The user input text.
     * @return The intent response containing intent class and confidence.
     */
    public IntentResponse detectIntent(String text) {
        log.info("Detecting intent for text: {}", text);
        
        // Optimization: Keyword Heuristic Check to skip LLM for obvious chat
        if (isObviousChat(text)) {
            log.info("Heuristic detected 'chat' intent, skipping LLM.");
            IntentResponse fastResp = new IntentResponse();
            fastResp.setIntent("chat");
            fastResp.setConfidence(1.0);
            return fastResp;
        }
        
        if ("chatglm".equalsIgnoreCase(intentConfig.getProvider())) {
            return detectIntentWithLLM(text);
        } else {
            return detectIntentLocal(text);
        }
    }

    private boolean isObviousChat(String text) {
        if (text == null || text.trim().isEmpty()) return true;
        String lower = text.toLowerCase();
        // Keywords that SUGGEST image/video generation
        String[] genKeywords = {
            "draw", "paint", "generate image", "create image", "make a picture", 
            "画", "绘", "图片", "做图",
            "video", "movie", "generate video", "create video",
            "视频", "生成视频"
        };
        
        for (String kw : genKeywords) {
            if (lower.contains(kw)) {
                return false; // Might be generation, verify with LLM
            }
        }
        return true; // Likely just chat
    }

    private IntentResponse detectIntentWithLLM(String text) {
        try {
            String prompt = String.format(
                "You are an intelligent intent classifier. \n" +
                "Classify the following user input into one of these categories:\n" +
                "- image_generation (for drawing, painting, creating images)\n" +
                "- video_generation (for making videos, movies)\n" +
                "- chat (for everything else, general knowledge, questions, conversation)\n\n" +
                "User Input: \"%s\"\n\n" +
                "Return ONLY a valid JSON object with no markdown formatting, like this:\n" +
                "{\"intent\": \"category_name\", \"confidence\": 0.95}", 
                text
            );

            String responseStr = chatGLMClient.generate(prompt);
            log.debug("LLM Intent Response: {}", responseStr);

            // Clean up markdown code blocks if present
            if (responseStr.startsWith("```json")) {
                responseStr = responseStr.substring(7);
                if (responseStr.endsWith("```")) {
                    responseStr = responseStr.substring(0, responseStr.length() - 3);
                }
            } else if (responseStr.startsWith("```")) {
                responseStr = responseStr.substring(3);
                if (responseStr.endsWith("```")) {
                    responseStr = responseStr.substring(0, responseStr.length() - 3);
                }
            }
            
            return objectMapper.readValue(responseStr.trim(), IntentResponse.class);

        } catch (Exception e) {
            log.error("Error using ChatGLM for intent recognition", e);
            IntentResponse fallback = new IntentResponse();
            fallback.setIntent("chat");
            fallback.setConfidence(0.0);
            return fallback;
        }
    }

    private IntentResponse detectIntentLocal(String text) {
        try {
            IntentRequest request = new IntentRequest(text);
            ResponseEntity<IntentResponse> response = restTemplate.postForEntity(
                    intentConfig.getIntentUrl(),
                    request,
                    IntentResponse.class
            );
            return response.getBody() != null ? response.getBody() : new IntentResponse();
        } catch (Exception e) {
            log.error("Error calling local intent service", e);
            IntentResponse fallback = new IntentResponse();
            fallback.setIntent("chat");
            return fallback;
        }
    }
}
