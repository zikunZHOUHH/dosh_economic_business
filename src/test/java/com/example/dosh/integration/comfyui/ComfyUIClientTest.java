package com.example.dosh.integration.comfyui;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Slf4j
public class ComfyUIClientTest {

    @Autowired
    private ComfyUIClient comfyUIClient;

    @Test
    void testGenerateImage() {
        String prompt = "Beautiful young European woman with honey blonde hair gracefully turning her head back over shoulder, gentle smile, bright eyes looking at camera. Hair flowing in slow motion as she turns. Soft natural lighting, clean background, cinematic slow-motion portrait.";
        
        log.info("Starting ComfyUI generation test with prompt: {}", prompt);
        
        try {
            List<String> urls = comfyUIClient.generateImage(prompt);
            
            assertNotNull(urls);
            assertFalse(urls.isEmpty(), "Should return at least one image URL");
            
            urls.forEach(url -> log.info("Generated Image URL: {}", url));
            
        } catch (Exception e) {
            log.error("Test failed", e);
            throw e;
        }
    }
}
