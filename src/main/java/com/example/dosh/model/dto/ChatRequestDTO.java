package com.example.dosh.model.dto;


import java.util.List;

public class ChatRequestDTO {
    private String message;
    private List<String> images; // Base64 encoded images
    private List<String> videos; // Base64 encoded videos (or URLs)
    private List<String> videoPaths; // Paths to uploaded videos

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public List<String> getVideos() { return videos; }
    public void setVideos(List<String> videos) { this.videos = videos; }

    public List<String> getVideoPaths() { return videoPaths; }
    public void setVideoPaths(List<String> videoPaths) { this.videoPaths = videoPaths; }
}