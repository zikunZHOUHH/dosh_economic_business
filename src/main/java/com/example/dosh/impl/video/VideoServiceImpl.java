package com.example.dosh.impl.video;

import com.example.dosh.integration.volcengine.VolcengineClient;
import com.example.dosh.model.dto.video.VideoClipDTO;
import com.example.dosh.service.video.VideoService;
import com.example.dosh.util.FFmpegUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
@Slf4j
public class VideoServiceImpl implements VideoService {

    private final FFmpegUtils ffmpegUtils;
    private final VolcengineClient volcengineClient;
    private final com.example.dosh.service.oss.MinioService minioService;
    private final String UPLOAD_DIR = "uploads";
    private final String CLIPS_DIR = "clips";
    private final String OUTPUT_DIR = "outputs";

    public VideoServiceImpl(FFmpegUtils ffmpegUtils, VolcengineClient volcengineClient, com.example.dosh.service.oss.MinioService minioService) {
        this.ffmpegUtils = ffmpegUtils;
        this.volcengineClient = volcengineClient;
        this.minioService = minioService;
        createDirs();
    }

    private void createDirs() {
        new File(UPLOAD_DIR).mkdirs();
        new File(CLIPS_DIR).mkdirs();
        new File(OUTPUT_DIR).mkdirs();
    }

    @Override
    public Map<String, Object> autoGenerateVideo(MultipartFile file, String prompt, double targetDuration) {
        try {
            // 1. Save Video to MinIO
            String videoUrl = uploadVideo(file);
            return processVideos(Collections.singletonList(videoUrl), prompt, targetDuration);

        } catch (Exception e) {
            log.error("Auto generation failed", e);
            throw new RuntimeException("Auto generation failed: " + e.getMessage());
        }
    }

    @Override
    public String uploadVideo(MultipartFile file) {
        try {
            log.info("Uploading video to MinIO...");
            String objectName = minioService.uploadFile(file);
            String ossUrl = minioService.getFileUrl(objectName, 60 * 24); // 24 hours expiry
            log.info("Video uploaded to MinIO: {}, URL: {}", objectName, ossUrl);
            return ossUrl;
        } catch (Exception e) {
            log.error("Failed to upload video to MinIO", e);
            throw new RuntimeException("Failed to upload video to MinIO: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> generateVideoFromPaths(List<String> videoUrls, String prompt, double targetDuration) {
        try {
            return processVideos(videoUrls, prompt, targetDuration);
        } catch (Exception e) {
            log.error("Video processing from paths failed", e);
            throw new RuntimeException("Processing failed: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> generateVideoFromBase64(List<String> base64Videos, String prompt, double targetDuration) {
        try {
            // Legacy support: throw exception to encourage new flow
             throw new UnsupportedOperationException("Base64 upload is deprecated. Please use FormData/MultipartFile upload.");
        } catch (Exception e) {
            log.error("Base64 video processing failed", e);
            throw new RuntimeException("Base64 video processing failed: " + e.getMessage());
        }
    }

    private Map<String, Object> processVideos(List<String> videoUrls, String prompt, double targetDuration) throws IOException, InterruptedException {
        List<String> allClipPaths = new ArrayList<>();
        int totalClipsCount = 0;

        for (String videoUrl : videoUrls) {
            // 2. Call AI Analysis (Now supports URL)
            List<VideoClipDTO> clips;
            try {
                clips = volcengineClient.analyzeVideo(videoUrl, prompt);
            } catch (Exception e) {
                 log.error("Analysis failed for URL: {}", videoUrl, e);
                 continue;
            }
            
            if (clips.isEmpty()) {
                log.warn("No clips found for video: {}", videoUrl);
                continue;
            }
            totalClipsCount += clips.size();

            // 3. Extract Clips
            for (VideoClipDTO clip : clips) {
                String clipName = "clip_" + UUID.randomUUID() + ".mp4";
                Path clipPath = Paths.get(CLIPS_DIR, clipName);
                
                double start = ffmpegUtils.timeStrToSeconds(clip.getStartTime());
                double end = ffmpegUtils.timeStrToSeconds(clip.getEndTime());
                double duration = end - start;
                
                // Use URL directly for extraction
                ffmpegUtils.extractClip(videoUrl, start, duration, clipPath.toString());
                allClipPaths.add(clipPath.toString());
            }
        }

        if (allClipPaths.isEmpty()) {
            throw new RuntimeException("AI returned no clips for any video");
        }

        // 4. Smart Trim
        List<String> finalClips = smartTrimClips(allClipPaths, targetDuration);

        // 5. Merge
        String outputFilename = "merged_" + UUID.randomUUID() + ".mp4";
        Path outputPath = Paths.get(OUTPUT_DIR, outputFilename);
        ffmpegUtils.mergeClips(finalClips, outputPath.toString());

        // 6. Upload Result to MinIO
        String finalOssUrl;
        try {
            File resultFile = outputPath.toFile();
            
            String objectName = minioService.uploadLocalFile(resultFile, "video/mp4");
            finalOssUrl = minioService.getFileUrl(objectName, 60 * 24);
            
            // Cleanup local merged file
            Files.deleteIfExists(outputPath);
            // Cleanup clips
            for (String clipPath : allClipPaths) { // Use allClipPaths to clean everything extracted
                Files.deleteIfExists(Paths.get(clipPath));
            }
            // Also cleanup trimmed clips if any (they are in finalClips but might be different files if trimmed)
             for (String clipPath : finalClips) {
                Files.deleteIfExists(Paths.get(clipPath));
            }
            
        } catch (Exception e) {
             throw new RuntimeException("Failed to upload result to MinIO: " + e.getMessage());
        }

        // 7. Response
        Map<String, Object> result = new HashMap<>();
        result.put("original_video", videoUrls.get(0));
        result.put("output_video", outputFilename); // Keeping name for reference
        result.put("preview_url", finalOssUrl); // Return OSS URL
        result.put("download_url", finalOssUrl); // Return OSS URL
        result.put("clips_count", totalClipsCount);
        
        return result;
    }

    private List<VideoClipDTO> mockAiAnalysis(String videoPath, String prompt) {
        log.info("Mock AI Analysis for: {}", videoPath);
        double duration = ffmpegUtils.getVideoDuration(videoPath);
        if (duration == 0) duration = 60.0; // Fallback

        int numClips = new Random().nextInt(3) + 1; // 1 to 3 clips
        List<VideoClipDTO> clips = new ArrayList<>();

        for (int i = 0; i < numClips; i++) {
            double start = new Random().nextDouble() * Math.max(0, duration - 10);
            double end = Math.min(start + 3 + new Random().nextDouble() * 5, duration); // 3 to 8 seconds

            String startTime = formatTime(start);
            String endTime = formatTime(end);
            
            clips.add(new VideoClipDTO(startTime, endTime, "Mock Event " + (i + 1), end - start));
        }
        return clips;
    }

    private String formatTime(double seconds) {
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        int ms = (int) ((seconds % 1) * 1000);
        return String.format("%02d:%02d.%03d", m, s, ms);
    }

    private List<String> smartTrimClips(List<String> clipPaths, double targetDuration) throws IOException, InterruptedException {
        List<Double> durations = new ArrayList<>();
        double totalDuration = 0;
        for (String path : clipPaths) {
            double d = ffmpegUtils.getVideoDuration(path);
            durations.add(d);
            totalDuration += d;
        }

        if (totalDuration <= targetDuration) {
            return clipPaths;
        }

        double needTrim = totalDuration - targetDuration;
        List<String> trimmedPaths = new ArrayList<>();
        Random rand = new Random();

        for (int i = 0; i < clipPaths.size(); i++) {
            String path = clipPaths.get(i);
            double originalDur = durations.get(i);
            double trimRatio = needTrim / totalDuration;
            double trimAmount = originalDur * trimRatio;

            // Random trim from start
            double trimStart = rand.nextDouble() * trimAmount;
            
            double newDuration = originalDur - trimAmount;
            if (newDuration < 0.5) {
                newDuration = 0.5;
                trimStart = 0;
            }

            String trimmedName = "trimmed_" + UUID.randomUUID() + ".mp4";
            Path trimmedPath = Paths.get(CLIPS_DIR, trimmedName);
            
            ffmpegUtils.extractClip(path, trimStart, newDuration, trimmedPath.toString());
            trimmedPaths.add(trimmedPath.toString());
        }

        return trimmedPaths;
    }
}