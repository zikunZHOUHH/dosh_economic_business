package com.example.dosh.integration.comfyui;

import com.example.dosh.config.ComfyUIConfig;
import com.example.dosh.service.oss.MinioService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComfyUIClient {

    private final ComfyUIConfig comfyUIConfig;
    private final MinioService minioService;
    private final ObjectMapper mapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.MINUTES) // Long timeout for generation
            .connectTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * Generate image(s) based on prompt.
     *
     * @param promptText The user's prompt.
     * @return List of MinIO URLs for the generated images.
     */
    public List<String> generateImage(String promptText) {
        String clientId = UUID.randomUUID().toString();
        List<String> resultUrls = new ArrayList<>();

        try {
            // 1. Prepare Workflow
            long seed = Math.abs(new Random().nextLong());
            String workflowJson = ComfyUIWorkflowTemplates.WAN_2_1_WORKFLOW
                    .replace("%POSITIVE_PROMPT%", promptText.replace("\"", "\\\"")) // Simple escape
                    .replace("%SEED%", String.valueOf(seed));
            
            JsonNode prompt = mapper.readTree(workflowJson);

            // 2. Connect WebSocket & Queue Prompt
            Map<String, ByteBuffer> imagesData = executeWorkflow(prompt, clientId);

            // 3. Process & Upload Images
            if (imagesData.isEmpty()) {
                throw new RuntimeException("No images received from ComfyUI.");
            }

            for (Map.Entry<String, ByteBuffer> entry : imagesData.entrySet()) {
                String key = entry.getKey();
                ByteBuffer data = entry.getValue();
                
                // Create temp file
                File tempFile = File.createTempFile("comfy_" + key + "_", ".png");
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    fos.write(bytes);
                }

                // Upload to MinIO
                log.info("Uploading generated image to MinIO: {}", tempFile.getName());
                String objectName = minioService.uploadLocalFile(tempFile, "image/png");
                String url = minioService.getFileUrl(objectName, 60 * 24); // 24 hours
                resultUrls.add(url);
                log.info("Image uploaded successfully: {}", url);

                // Cleanup
                Files.deleteIfExists(tempFile.toPath());
            }

        } catch (Exception e) {
            log.error("ComfyUI generation failed", e);
            throw new RuntimeException("Image generation failed: " + e.getMessage());
        }

        return resultUrls;
    }

    private Map<String, ByteBuffer> executeWorkflow(JsonNode prompt, String clientId) throws Exception {
        // 0. Free Memory (Try to unload models before starting)
        unloadModels();

        Map<String, ByteBuffer> outputImages = new ConcurrentHashMap<>();
        
        // 1. WebSocket Listener (Connect BEFORE queuing prompt)
        CountDownLatch latch = new CountDownLatch(1);
        String serverAddress = comfyUIConfig.getServerAddress();
        String wsUrl = String.format("ws://%s/ws?clientId=%s", serverAddress, clientId);

        // We need a container for promptId because it's available only after queueing
        final StringBuilder promptIdRef = new StringBuilder();

        WebSocketClient wsClient = new WebSocketClient(new URI(wsUrl)) {
            private String currentNode = "";

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                log.info("ComfyUI WebSocket Connected");
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonNode msg = mapper.readTree(message);
                    if (msg.has("type") && "executing".equals(msg.get("type").asText())) {
                        JsonNode data = msg.get("data");
                        String currentPromptId = promptIdRef.toString();
                        if (!currentPromptId.isEmpty() && data.has("prompt_id") && data.get("prompt_id").asText().equals(currentPromptId)) {
                            if (data.get("node").isNull()) {
                                log.info("ComfyUI Execution finished.");
                                latch.countDown();
                            } else {
                                currentNode = data.get("node").asText();
                                log.debug("Executing node: {}", currentNode);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error parsing WS message", e);
                }
            }

            @Override
            public void onMessage(ByteBuffer bytes) {
                 // Binary data via WebSocket (Preview or SaveImageWebsocket)
                 log.debug("Received binary data via WS ({} bytes)", bytes.remaining());
                 if (bytes.remaining() > 8) {
                    bytes.position(8); // Skip header
                    ByteBuffer imageData = bytes.slice();
                    String key = currentNode.isEmpty() ? "unknown_" + System.currentTimeMillis() : currentNode;
                    if (outputImages.containsKey(key)) {
                         key = key + "_" + System.currentTimeMillis();
                    }
                    outputImages.put(key, imageData);
                 }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.info("ComfyUI WebSocket Closed: {}", reason);
                if (latch.getCount() > 0) latch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                log.error("ComfyUI WebSocket Error", ex);
            }
        };

        wsClient.connectBlocking();
        
        // 2. Queue Prompt
        JsonNode responseNode = queuePrompt(prompt, clientId);
        String promptId = responseNode.get("prompt_id").asText();
        promptIdRef.append(promptId);
        log.info("ComfyUI Prompt queued. ID: {}", promptId);

        log.info("Waiting for ComfyUI execution...");
        
        // Wait up to 60 minutes (configurable?)
        boolean completed = latch.await(60, TimeUnit.MINUTES);
        wsClient.close();

        if (!completed) {
            throw new RuntimeException("ComfyUI execution timed out.");
        }

        // 3. Fallback: Fetch from History (if not received via WS)
        // Usually SaveImage node writes to disk and we can fetch via history.
        // Even if we got some via WS, checking history ensures we get the final outputs.
        fetchImagesFromHistory(promptId, outputImages);

        return outputImages;
    }

    private JsonNode queuePrompt(JsonNode prompt, String clientId) throws IOException {
        String url = String.format("http://%s/prompt", comfyUIConfig.getServerAddress());
        ObjectNode p = mapper.createObjectNode();
        p.set("prompt", prompt);
        p.put("client_id", clientId);

        RequestBody body = RequestBody.create(p.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Queue prompt failed: " + response);
            return mapper.readTree(response.body().string());
        }
    }

    private void fetchImagesFromHistory(String promptId, Map<String, ByteBuffer> imagesMap) {
        try {
            String historyUrl = String.format("http://%s/history/%s", comfyUIConfig.getServerAddress(), promptId);
            Request request = new Request.Builder().url(historyUrl).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return;

                JsonNode historyRoot = mapper.readTree(response.body().string());
                JsonNode promptHistory = historyRoot.get(promptId);
                if (promptHistory == null || !promptHistory.has("outputs")) return;

                JsonNode outputs = promptHistory.get("outputs");
                outputs.fields().forEachRemaining(entry -> {
                    String nodeId = entry.getKey();
                    JsonNode nodeOutput = entry.getValue();
                    
                    processOutputFiles(nodeId, nodeOutput, "images", imagesMap);
                    processOutputFiles(nodeId, nodeOutput, "videos", imagesMap); // Wan2.1 might output video?
                    processOutputFiles(nodeId, nodeOutput, "gifs", imagesMap);
                });
            }
        } catch (Exception e) {
            log.error("Error fetching history", e);
        }
    }

    private void processOutputFiles(String nodeId, JsonNode nodeOutput, String fieldName, Map<String, ByteBuffer> imagesMap) {
        if (nodeOutput.has(fieldName)) {
            for (JsonNode file : nodeOutput.get(fieldName)) {
                String filename = file.get("filename").asText();
                String subfolder = file.get("subfolder").asText();
                String type = file.get("type").asText();
                
                try {
                    byte[] fileData = downloadFile(filename, subfolder, type);
                    String key = nodeId + "_" + filename;
                    // Only add if not already present (prefer WebSocket if it was faster/same)
                    // Actually, History is more reliable for final output. Overwrite.
                    imagesMap.put(key, ByteBuffer.wrap(fileData));
                } catch (IOException e) {
                    log.error("Failed to download {}", filename, e);
                }
            }
        }
    }

    private byte[] downloadFile(String filename, String subfolder, String type) throws IOException {
        String url = String.format("http://%s/view?filename=%s&subfolder=%s&type=%s", 
                comfyUIConfig.getServerAddress(), filename, subfolder, type);
        
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Download failed: " + response);
            return response.body().bytes();
        }
    }

    private void unloadModels() {
        try {
            String url = String.format("http://%s/free", comfyUIConfig.getServerAddress());
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create("{\"unload_models\":true, \"free_memory\":true}", MediaType.get("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("Successfully requested ComfyUI to free memory/unload models.");
                } else {
                    log.warn("Failed to free memory: {}", response.code());
                }
            }
        } catch (Exception e) {
            log.warn("Error triggering memory cleanup: {}", e.getMessage());
        }
    }
}
