package com.example.dosh.service.video;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

public interface VideoService {
    Map<String, Object> autoGenerateVideo(MultipartFile file, String prompt, double targetDuration);
    Map<String, Object> generateVideoFromBase64(List<String> base64Videos, String prompt, double targetDuration);
    Map<String, Object> generateVideoFromPaths(List<String> videoPaths, String prompt, double targetDuration);
    String uploadVideo(MultipartFile file);
}
