package com.example.dosh.impl.oss;
import com.example.dosh.config.MinioConfig;
import com.example.dosh.service.oss.MinioService;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Expiration;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.LifecycleRule;
import io.minio.messages.RuleFilter;
import io.minio.messages.Status;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.util.Exceptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioServiceImpl implements MinioService {
    @Autowired
    private MinioConfig minioConfig;
    @Autowired
    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
            }

            // Set lifecycle policy to expire files after 7 days
            List<LifecycleRule> rules = new ArrayList<>();
            rules.add(new LifecycleRule(
                    Status.ENABLED,
                    null,
                    new Expiration((ZonedDateTime) null, 1, null),
                    new RuleFilter(""),
                    "expire-1-days",
                    null,
                    null,
                    null));

            LifecycleConfiguration config = new LifecycleConfiguration(rules);

            minioClient.setBucketLifecycle(
                    SetBucketLifecycleArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .config(config)
                            .build());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * upload file to minio
     * @param file
     * @return
     * @throws Exception
     */
    public String uploadFile(MultipartFile file) throws Exception {
        if(!minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build())){
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        // store path: bucketName/objectName (can add path before UUID)
        String objectName = UUID.randomUUID().toString() + fileExtension;

        // upload
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());

        return objectName;
    }

    @Override
    public String uploadLocalFile(java.io.File file, String contentType) throws Exception {
        if(!minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build())){
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
        }

        String originalFilename = file.getName();
        String fileExtension = "";
        if (originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String objectName = UUID.randomUUID().toString() + fileExtension;

        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucket())
                            .object(objectName)
                            .stream(is, file.length(), -1)
                            .contentType(contentType)
                            .build());
        }

        return objectName;
    }

    /**
     * get file url from minio
     * @param objectName
     * @param expiry
     * @return
     * @throws Exception
     */
    public String getFileUrl(String objectName, int expiry) throws Exception{
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .expiry(expiry, TimeUnit.MINUTES)
                        .build());
    }


    /**
     * delete file from minio
     * @param objectName
     * @throws Exception
     */
    public void deleteFile(String objectName) throws Exception{
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(objectName)
                        .build());
    }

}
