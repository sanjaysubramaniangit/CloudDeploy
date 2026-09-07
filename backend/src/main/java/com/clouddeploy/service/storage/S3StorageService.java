package com.clouddeploy.service.storage;

import com.clouddeploy.exception.StorageConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Service
public class S3StorageService implements StorageService {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-duration-minutes:15}")
    private int defaultPresignedUrlDurationMinutes;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    @Override
    public boolean isConfigured() {
        return bucketName != null && !bucketName.trim().isEmpty();
    }

    private void assertConfigured() {
        if (!isConfigured()) {
            throw new StorageConfigurationException(
                    "AWS S3 storage is not configured. Please set AWS_REGION and AWS_S3_BUCKET."
            );
        }
    }

    private synchronized S3Client getS3Client() {
        assertConfigured();
        if (s3Client == null) {
            try {
                s3Client = S3Client.builder()
                        .region(Region.of(awsRegion))
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
            } catch (Exception e) {
                log.error("Failed to initialize AWS S3 client", e);
                throw new StorageConfigurationException("Unable to initialize AWS S3 client: " + e.getMessage());
            }
        }
        return s3Client;
    }

    private synchronized S3Presigner getS3Presigner() {
        assertConfigured();
        if (s3Presigner == null) {
            try {
                s3Presigner = S3Presigner.builder()
                        .region(Region.of(awsRegion))
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build();
            } catch (Exception e) {
                log.error("Failed to initialize AWS S3 presigner", e);
                throw new StorageConfigurationException("Unable to initialize AWS S3 presigner: " + e.getMessage());
            }
        }
        return s3Presigner;
    }

    @Override
    public String uploadFile(String key, InputStream inputStream, long contentLength, String contentType) {
        assertConfigured();
        try {
            RequestBody requestBody = RequestBody.fromInputStream(inputStream, contentLength);
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();

            getS3Client().putObject(putRequest, requestBody);
            log.info("Successfully uploaded object to S3: {} in bucket: {}", key, bucketName);
            return key;
        } catch (StorageConfigurationException sce) {
            throw sce;
        } catch (Exception e) {
            log.error("Error uploading file to S3 with key {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to upload file to S3 storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String key) {
        assertConfigured();
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            getS3Client().deleteObject(deleteRequest);
            log.info("Successfully deleted object from S3: {} in bucket: {}", key, bucketName);
        } catch (StorageConfigurationException sce) {
            throw sce;
        } catch (Exception e) {
            log.error("Error deleting file from S3 with key {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to delete file from S3 storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, Duration expiration) {
        assertConfigured();
        try {
            Duration duration = expiration != null ? expiration : Duration.ofMinutes(defaultPresignedUrlDurationMinutes);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presigned = getS3Presigner().presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (StorageConfigurationException sce) {
            throw sce;
        } catch (Exception e) {
            log.error("Error generating presigned URL for key {}: {}", key, e.getMessage());
            throw new RuntimeException("Failed to generate pre-signed URL: " + e.getMessage(), e);
        }
    }
}
