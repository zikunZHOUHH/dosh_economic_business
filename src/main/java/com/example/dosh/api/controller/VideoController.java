package com.example.dosh.api.controller;

import com.example.dosh.service.video.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
@Slf4j
public class VideoController {

    private final VideoService videoService;

    @PostMapping("/auto-generate")
    public ResponseEntity<Map<String, Object>> autoGenerate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "Highlight interesting parts") String prompt,
            @RequestParam(value = "targetDuration", defaultValue = "60.0") Double targetDuration) {
        
        log.info("Received auto-generate request for file: {}", file.getOriginalFilename());
        Map<String, Object> result = videoService.autoGenerateVideo(file, prompt, targetDuration);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadVideo(@RequestParam("file") MultipartFile file) {
        log.info("Received upload request for file: {}", file.getOriginalFilename());
        String path = videoService.uploadVideo(file);
        return ResponseEntity.ok(Map.of("path", path));
    }

    @GetMapping("/preview/{filename}")
    public ResponseEntity<Resource> previewVideo(@PathVariable String filename) {
        return serveFile(filename, "outputs");
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadVideo(@PathVariable String filename) {
        return serveFile(filename, "outputs", true);
    }
    
    @GetMapping("/preview-clip/{filename}")
    public ResponseEntity<Resource> previewClip(@PathVariable String filename) {
        return serveFile(filename, "clips");
    }

    private ResponseEntity<Resource> serveFile(String filename, String dir) {
        return serveFile(filename, dir, false);
    }

    private ResponseEntity<Resource> serveFile(String filename, String dir, boolean download) {
        try {
            Path path = Paths.get(dir, filename);
            File file = path.toFile();

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            HttpHeaders headers = new HttpHeaders();
            
            if (download) {
                headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            } else {
                headers.add(HttpHeaders.CONTENT_TYPE, "video/mp4");
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .body(resource);

        } catch (Exception e) {
            log.error("Error serving file: {}", filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
