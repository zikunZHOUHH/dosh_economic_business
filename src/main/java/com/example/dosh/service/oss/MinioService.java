package com.example.dosh.service.oss;
import org.springframework.web.multipart.MultipartFile;


public interface MinioService {
    String uploadFile(MultipartFile file) throws Exception;

    String uploadLocalFile(java.io.File file, String contentType) throws Exception;

    String getFileUrl(String objectName, int expiry) throws Exception;

    void deleteFile(String objectName) throws Exception;
}
