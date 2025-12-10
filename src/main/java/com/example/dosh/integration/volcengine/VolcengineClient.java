package com.example.dosh.integration.volcengine;

import com.example.dosh.config.VolcengineConfig;
import com.example.dosh.model.dto.video.VideoClipDTO;
import com.example.dosh.util.FFmpegUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class VolcengineClient {

    private final VolcengineConfig config;
    private final ObjectMapper mapper;
    private final FFmpegUtils ffmpegUtils;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS) // 10 minutes for video analysis
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public List<VideoClipDTO> analyzeVideo(String videoUrlOrPath, String prompt) {
        log.info("Starting Volcengine AI analysis for: {}", videoUrlOrPath);

        try {
            String videoContentString;
            // Check if input is a URL (starts with http:// or https://)
            if (videoUrlOrPath.startsWith("http://") || videoUrlOrPath.startsWith("https://")) {
                boolean isLocal = false;
                try {
                    java.net.URL url = new java.net.URL(videoUrlOrPath);
                    String host = url.getHost();
                    isLocal = "localhost".equalsIgnoreCase(host) ||
                            "127.0.0.1".equals(host) ||
                            "::1".equals(host) ||
                            host.startsWith("192.168.") ||
                            host.startsWith("10.");
                } catch (Exception ignored) {
                }

                if (isLocal) {
                    log.info("Detected local URL, falling back to download-and-Base64 strategy for Cloud AI compatibility.");
                    // Download file to temp
                    File tempFile = File.createTempFile("volc_temp_", ".mp4");
                    try {
                        Request request = new Request.Builder().url(videoUrlOrPath).build();
                        try (Response response = client.newCall(request).execute()) {
                            if (!response.isSuccessful()) throw new IOException("Failed to download local video: " + response);
                            Files.copy(response.body().byteStream(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }

                        // Convert to Base64
                        byte[] fileContent = Files.readAllBytes(tempFile.toPath());
                        String videoBase64 = Base64.getEncoder().encodeToString(fileContent);
                        videoContentString = "data:video/mp4;base64," + videoBase64;
                    } finally {
                        tempFile.delete();
                    }
                } else {
                    // Use URL directly
                    videoContentString = videoUrlOrPath;
                }
            } else {
                // Fallback to local file processing (Base64) - Legacy support or if file is local
                File videoFile = new File(videoUrlOrPath);
                 if (videoFile.length() > 50 * 1024 * 1024) {
                    log.warn("Video file too large: {} MB", videoFile.length() / (1024.0 * 1024.0));
                 }
                 byte[] fileContent = Files.readAllBytes(videoFile.toPath());
                 String videoBase64 = Base64.getEncoder().encodeToString(fileContent);
                 videoContentString = "data:video/mp4;base64," + videoBase64;
            }

            // 2. Build Request Body
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", config.getModel());
            
            Map<String, Object> inputMessage = new HashMap<>();
            inputMessage.put("role", "user");
            
            List<Map<String, Object>> contentList = new ArrayList<>();
            
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "input_text");
            textContent.put("text", "这是你的任务：请根据用户的描述以JSON格式输出每个片段的开始时间（start_time）、结束时间（end_time）、事件描述（event），时间戳使用mm:ss.SSS格式，如果没有满足要求的片段，请返回空列表。不管视频内容是什么都用中文回复，确保输出的JSON格式正确且可解析。用户描述如下：" + prompt);
            contentList.add(textContent);

            Map<String, Object> videoContent = new HashMap<>();
            videoContent.put("type", "input_video");
            videoContent.put("video_url", videoContentString);
            videoContent.put("fps", 1);
            contentList.add(videoContent);

            inputMessage.put("content", contentList);
            payload.put("input", Collections.singletonList(inputMessage));

            String jsonBody = mapper.writeValueAsString(payload);
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, jsonBody);

            // 3. Send Request
            Request request = new Request.Builder()
                    .url(config.getApiUrl())
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "null";
                    log.error("Volcengine API call failed: code={}, body={}", response.code(), errorBody);
                    throw new IOException("Volcengine API failed: " + response.code());
                }

                String responseBody = response.body().string();
                log.info("Volcengine raw response received");

                // 4. Parse Response
                return parseResponse(responseBody);
            }

        } catch (Exception e) {
            log.error("Volcengine analysis failed", e);
            throw new RuntimeException("AI analysis failed: " + e.getMessage());
        }
    }

    private List<VideoClipDTO> parseResponse(String responseBody) throws IOException {
        JsonNode root = mapper.readTree(responseBody);
        
        String jsonContent = null;
        
        // 1. Try OpenAI format (choices -> message -> content)
        if (root.has("choices") && root.get("choices").isArray()) {
            JsonNode choices = root.get("choices");
            if (choices.size() > 0) {
                JsonNode message = choices.get(0).path("message");
                if (message.has("content")) {
                    jsonContent = message.get("content").asText();
                }
            }
        }
        
        // 2. Fallback to "output" format
        if (jsonContent == null && root.has("output") && root.get("output").isArray()) {
             JsonNode outputArray = root.get("output");
             // Usually the last message contains the assistant response
             for (JsonNode item : outputArray) {
                 if (item.has("message") && "assistant".equals(item.path("message").path("role").asText())) {
                      jsonContent = item.path("message").path("content").get(0).path("text").asText();
                 } else if (item.has("content")) {
                     // Check if content is array or object
                     JsonNode contentNode = item.get("content");
                     if (contentNode.isArray() && contentNode.size() > 0 && contentNode.get(0).has("text")) {
                         jsonContent = contentNode.get(0).get("text").asText();
                     }
                 }
             }
             
             // Fallback to Python logic: output[1].content[0].text
             if (jsonContent == null && outputArray.size() > 1) {
                 jsonContent = outputArray.get(1).path("content").get(0).path("text").asText();
             }
        }
        
        if (jsonContent == null) {
            log.error("Could not extract text content from response: {}", responseBody);
            return Collections.emptyList();
        }

        // Clean up markdown code blocks
        jsonContent = jsonContent.trim();
        if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.replaceAll("^```(json)?", "").replaceAll("```$", "");
        }
        jsonContent = jsonContent.trim();

        log.info("Extracted JSON content: {}", jsonContent);

        try {
            // Try to parse as List
            List<VideoClipDTO> clips = mapper.readValue(jsonContent, new TypeReference<List<VideoClipDTO>>(){});
            
            // Calculate duration for each clip
            for (VideoClipDTO clip : clips) {
                double start = ffmpegUtils.timeStrToSeconds(clip.getStartTime());
                double end = ffmpegUtils.timeStrToSeconds(clip.getEndTime());
                clip.setDuration(end - start);
            }
            
            return clips;
        } catch (Exception e) {
            // Try to parse as Object wrapping the list
            JsonNode node = mapper.readTree(jsonContent);
            if (node.has("clips")) {
                 List<VideoClipDTO> clips = mapper.readValue(node.get("clips").traverse(), new TypeReference<List<VideoClipDTO>>(){});
                 return clips;
            }
            log.error("Failed to parse JSON content", e);
            return Collections.emptyList();
        }
    }
}
