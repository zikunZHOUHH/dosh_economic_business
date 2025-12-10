package com.example.dosh.impl.chat;

import com.example.dosh.integration.chatglm.ChatGLMClient;
import com.example.dosh.integration.comfyui.ComfyUIClient;
import com.example.dosh.integration.intent.IntentClient;
import com.example.dosh.model.dto.ChatRequestDTO;
import com.example.dosh.model.dto.intent.IntentResponse;
import com.example.dosh.service.chat.ChatService;
import com.example.dosh.service.video.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ComfyUIClient comfyUIClient;
    private final IntentClient intentClient;
    private final ChatGLMClient chatGLMClient;
    private final VideoService videoService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public SseEmitter streamChat(ChatRequestDTO request) {
        SseEmitter emitter = new SseEmitter(0L); // No timeout
        String prompt = request.getMessage();

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            log.info("Received {} images in stream request", request.getImages().size());
        }
        if (request.getVideos() != null && !request.getVideos().isEmpty()) {
            log.info("Received {} videos in stream request", request.getVideos().size());
        }

        executor.submit(() -> {
            try {
                // 1. Intent Recognition
                IntentResponse intentResponse = intentClient.detectIntent(prompt);
                String intent = intentResponse.getIntent();
                log.info("Detected intent for stream: {}", intent);

                // 2. Routing based on intent
                if ("image_generation".equalsIgnoreCase(intent)) {
                    emitter.send(SseEmitter.event().data("Starting image generation... Please wait, this may take a few minutes."));

                    try {
                        java.util.List<String> imageUrls = comfyUIClient.generateImage(prompt);
                        
                        StringBuilder responseHtml = new StringBuilder("Image generation complete! Here are your images: <br/><br/>");
                        responseHtml.append("<div style=\"display: flex; flex-wrap: wrap; gap: 10px;\">");
                        
                        for (String url : imageUrls) {
                            responseHtml.append("<img src=\"").append(url).append("\" style=\"max-width: 100%; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-bottom: 10px;\" />");
                        }
                        responseHtml.append("</div>");

                        emitter.send(SseEmitter.event().data(responseHtml.toString()));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Image generation failed", e);
                        emitter.send(SseEmitter.event().data("Sorry, image generation failed: " + e.getMessage()));
                        emitter.completeWithError(e);
                    }
                } else if ("video_generation".equalsIgnoreCase(intent)) {
                    emitter.send(SseEmitter.event().data("Processing videos... This may take a moment."));

                    boolean hasVideos = (request.getVideos() != null && !request.getVideos().isEmpty());
                    boolean hasVideoPaths = (request.getVideoPaths() != null && !request.getVideoPaths().isEmpty());

                    if (!hasVideos && !hasVideoPaths) {
                        emitter.send(SseEmitter.event().data("Please provide video(s) for editing."));
                        emitter.complete();
                        return;
                    }

                    try {
                        Map<String, Object> result;
                        if (hasVideoPaths) {
                            result = videoService.generateVideoFromPaths(request.getVideoPaths(), prompt, 60.0);
                        } else {
                            result = videoService.generateVideoFromBase64(request.getVideos(), prompt, 60.0);
                        }

                        String previewUrl = (String) result.get("preview_url");
                        String downloadUrl = (String) result.get("download_url");

                        // Check if URLs are already absolute (MinIO URLs usually are)
                        String finalPreviewUrl = previewUrl.startsWith("http") ? previewUrl : "http://localhost:8080" + previewUrl;
                        String finalDownloadUrl = downloadUrl.startsWith("http") ? downloadUrl : "http://localhost:8080" + downloadUrl;

                        String responseHtml = "Video processing complete! Here is your video: <br/><br/>" +
                                "<div style=\"display: flex; flex-direction: column; align-items: flex-start; gap: 10px;\">" +
                                "<video controls src=\"" + finalPreviewUrl + "\" style=\"max-width: 100%; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);\"></video>" +
                                "<a href=\"" + finalDownloadUrl + "\" download style=\"display: inline-block; padding: 10px 20px; background-color: #007bff; color: white; text-decoration: none; border-radius: 5px; font-weight: 500; transition: background-color 0.2s;\" onmouseover=\"this.style.backgroundColor='#0056b3'\" onmouseout=\"this.style.backgroundColor='#007bff'\">Download Video</a>" +
                                "</div>";

                        emitter.send(SseEmitter.event().data(responseHtml));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Video generation failed in stream chat", e);
                        emitter.send(SseEmitter.event().data("Sorry, video processing failed: " + e.getMessage()));
                        emitter.completeWithError(e);
                    }
                } else {
                    // Default to Chat/LLM (ChatGLM) Stream
                    chatGLMClient.streamGenerate(prompt, emitter);
                }
            } catch (Exception e) {
                log.error("Error in stream chat", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
