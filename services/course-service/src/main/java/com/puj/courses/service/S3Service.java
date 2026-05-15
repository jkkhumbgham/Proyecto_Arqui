package com.puj.courses.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
public class S3Service {

    private S3Presigner presigner;
    private String      bucket;

    @PostConstruct
    void init() {
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        this.bucket   = System.getenv().getOrDefault("AWS_S3_BUCKET", "puj-learning-resources");
        this.presigner = S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }

    public String generateUploadUrl(String courseId, String fileName, String contentType) {
        String key = "courses/" + courseId + "/" + UUID.randomUUID() + "/" + fileName;

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putReq)
                .build();

        return presigner.presignPutObject(presignReq).url().toString();
    }

    public String generateDownloadUrl(String s3Key) {
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getReq)
                .build();

        return presigner.presignGetObject(presignReq).url().toString();
    }
}
