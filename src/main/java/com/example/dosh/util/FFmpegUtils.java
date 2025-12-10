package com.example.dosh.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FFmpegUtils {

    @org.springframework.beans.factory.annotation.Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @org.springframework.beans.factory.annotation.Value("${ffprobe.path:ffprobe}")
    private String ffprobePath;

    public double getVideoDuration(String videoPath) {
        List<String> command = new ArrayList<>();
        command.add(ffprobePath);
        command.add("-v");
        command.add("error");
        command.add("-show_entries");
        command.add("format=duration");
        command.add("-of");
        command.add("default=noprint_wrappers=1:nokey=1");
        command.add(videoPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            
            if (line != null && !line.trim().isEmpty() && !"N/A".equalsIgnoreCase(line.trim())) {
                try {
                    return Double.parseDouble(line.trim());
                } catch (NumberFormatException e) {
                    log.warn("Invalid duration format from ffprobe: {}", line);
                }
            }
        } catch (Exception e) {
            log.error("Failed to get video duration", e);
        }
        return 0.0;
    }

    public void extractClip(String videoPath, double start, double duration, String outputPath) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y"); // Overwrite
        command.add("-ss");
        command.add(String.valueOf(start));
        command.add("-i");
        command.add(videoPath);
        command.add("-t");
        command.add(String.valueOf(duration));
        // Add robust timestamp handling
        command.add("-avoid_negative_ts");
        command.add("make_zero");
        // Enforce consistent framerate for concatenation
        command.add("-r");
        command.add("30");
        command.add("-c:v");
        command.add("libx264");
        command.add("-c:a");
        command.add("aac");
        command.add("-strict");
        command.add("experimental");
        command.add(outputPath);

        runCommand(command);
    }

    public void mergeClips(List<String> clipPaths, String outputPath) throws IOException, InterruptedException {
        // Create concat file
        File concatFile = new File("clips/concat_" + UUID.randomUUID() + ".txt");
        if (concatFile.getParentFile() != null) {
            concatFile.getParentFile().mkdirs();
        }
        
        StringBuilder sb = new StringBuilder();
        for (String path : clipPaths) {
            // Escape paths for ffmpeg concat file
            String absPath = new File(path).getAbsolutePath().replace("\\", "/");
            sb.append("file '").append(absPath).append("'\n");
        }
        Files.writeString(concatFile.toPath(), sb.toString());

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-f");
        command.add("concat");
        command.add("-safe");
        command.add("0");
        command.add("-i");
        command.add(concatFile.getAbsolutePath());
        // Re-encode during merge to ensure smooth transitions and fix audio/video sync gaps
        command.add("-c:v");
        command.add("libx264");
        command.add("-c:a");
        command.add("aac");
        command.add("-strict");
        command.add("experimental");
        command.add(outputPath);

        try {
            runCommand(command);
        } finally {
            concatFile.delete();
        }
    }
    
    // Convert time string "MM:SS.mmm" or "HH:MM:SS.mmm" to seconds
    public double timeStrToSeconds(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return 0.0;
        }
        try {
            String[] parts = timeStr.split(":");
            double seconds = 0.0;
            if (parts.length == 3) {
                seconds += Double.parseDouble(parts[0]) * 3600;
                seconds += Double.parseDouble(parts[1]) * 60;
                seconds += Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                seconds += Double.parseDouble(parts[0]) * 60;
                seconds += Double.parseDouble(parts[1]);
            } else {
                seconds = Double.parseDouble(timeStr);
            }
            return seconds;
        } catch (Exception e) {
            log.error("Error parsing time string: {}", timeStr);
            return 0.0;
        }
    }

    private void runCommand(List<String> command) throws IOException, InterruptedException {
        log.info("Running command: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            if (e.getMessage().contains("CreateProcess error=2")) {
                log.error("FFmpeg not found at path: {}", command.get(0));
                throw new IOException("Cannot run program \"" + command.get(0) + "\". Please ensure FFmpeg is installed and configured in application.properties. You can download it from https://ffmpeg.org/download.html", e);
            }
            throw e;
        }
        
        // Consume output to prevent blocking
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // log.debug(line); // Verbose
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("FFmpeg command failed with exit code " + exitCode);
        }
    }
}
