package com.clouddeploy.service.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    String uploadFile(String key, InputStream inputStream, long contentLength, String contentType);

    void deleteFile(String key);

    String generatePresignedUrl(String key, Duration expiration);

    boolean isConfigured();
}
