package com.clouddeploy.service;

import com.clouddeploy.dto.FileAccessUrlResponse;
import com.clouddeploy.dto.FileResponse;
import com.clouddeploy.entity.Application;
import com.clouddeploy.entity.StoredFile;
import com.clouddeploy.entity.User;
import com.clouddeploy.exception.FileValidationException;
import com.clouddeploy.exception.ResourceNotFoundException;
import com.clouddeploy.repository.StoredFileRepository;
import com.clouddeploy.repository.UserRepository;
import com.clouddeploy.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private static final Set<String> DISALLOWED_EXTENSIONS = Set.of(
            "exe", "sh", "bat", "cmd", "jar", "jsp", "dll", "so", "com", "vbs", "msi"
    );

    @Value("${aws.s3.presigned-url-duration-minutes:15}")
    private int presignedUrlDurationMinutes;

    private final StorageService storageService;
    private final StoredFileRepository storedFileRepository;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;

    @Transactional
    public FileResponse uploadFile(Long applicationId, MultipartFile file, String userEmail) {
        User user = getUserByEmail(userEmail);
        Application application = applicationService.findApplicationAndVerifyOwnership(applicationId, user);

        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = sanitizeFilename(originalFilename);
        String s3Key = String.format("applications/%d/files/%s-%s", applicationId, UUID.randomUUID(), sanitizedFilename);

        try {
            storageService.uploadFile(s3Key, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            log.error("Failed to read file input stream: {}", e.getMessage());
            throw new RuntimeException("Failed to read file input stream: " + e.getMessage(), e);
        }

        StoredFile storedFile = StoredFile.builder()
                .application(application)
                .fileName(sanitizedFilename)
                .s3Key(s3Key)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .fileSize(file.getSize())
                .uploadedBy(user)
                .uploadedAt(LocalDateTime.now())
                .build();

        StoredFile saved = storedFileRepository.save(storedFile);
        log.info("Persisted StoredFile metadata for file: {} (id: {})", saved.getFileName(), saved.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FileResponse> getFilesByApplication(Long applicationId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Application application = applicationService.findApplicationAndVerifyOwnership(applicationId, user);

        return storedFileRepository.findByApplicationOrderByUploadedAtDesc(application)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteFile(Long fileId, String userEmail) {
        User user = getUserByEmail(userEmail);
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

        // Authorize via parent application ownership
        applicationService.findApplicationAndVerifyOwnership(storedFile.getApplication().getId(), user);

        // Delete S3 object first; if storage deletion throws an error, metadata is preserved
        storageService.deleteFile(storedFile.getS3Key());

        // Delete database metadata
        storedFileRepository.delete(storedFile);
        log.info("Successfully deleted file {} (id: {})", storedFile.getFileName(), fileId);
    }

    @Transactional(readOnly = true)
    public FileAccessUrlResponse generatePresignedAccessUrl(Long fileId, String userEmail) {
        User user = getUserByEmail(userEmail);
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

        // Authorize via parent application ownership
        applicationService.findApplicationAndVerifyOwnership(storedFile.getApplication().getId(), user);

        Duration duration = Duration.ofMinutes(presignedUrlDurationMinutes);
        String presignedUrl = storageService.generatePresignedUrl(storedFile.getS3Key(), duration);

        return FileAccessUrlResponse.builder()
                .fileId(storedFile.getId())
                .fileName(storedFile.getFileName())
                .accessUrl(presignedUrl)
                .expiresAt(LocalDateTime.now().plus(duration))
                .build();
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("Cannot upload an empty file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileValidationException("File size exceeds maximum permitted upload limit of 10MB");
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            throw new FileValidationException("Filename cannot be empty");
        }

        // Path traversal detection
        if (rawFilename.contains("..") || rawFilename.contains("/") || rawFilename.contains("\\")) {
            throw new FileValidationException("Filename contains invalid path traversal characters");
        }

        // Extension validation
        String extension = StringUtils.getFilenameExtension(rawFilename);
        if (extension != null && DISALLOWED_EXTENSIONS.contains(extension.toLowerCase().trim())) {
            throw new FileValidationException(
                    String.format("File type '.%s' is dangerous and not permitted for upload", extension.toLowerCase())
            );
        }
    }

    public String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        String baseName = StringUtils.getFilename(filename);
        if (baseName == null) baseName = filename;

        // Keep only alphanumeric, dots, dashes, underscores
        String sanitized = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.trim().isEmpty() || sanitized.equals(".")) {
            sanitized = "unnamed_file";
        }
        return sanitized;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    public FileResponse mapToResponse(StoredFile file) {
        return FileResponse.builder()
                .id(file.getId())
                .applicationId(file.getApplication().getId())
                .fileName(file.getFileName())
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .uploadedAt(file.getUploadedAt())
                .uploadedByEmail(file.getUploadedBy() != null ? file.getUploadedBy().getEmail() : null)
                .uploadedByName(file.getUploadedBy() != null ? file.getUploadedBy().getName() : null)
                .build();
    }
}
