package com.clouddeploy.controller;

import com.clouddeploy.dto.FileAccessUrlResponse;
import com.clouddeploy.dto.FileResponse;
import com.clouddeploy.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/api/applications/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> uploadFile(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return new ResponseEntity<>(
                fileStorageService.uploadFile(id, file, authentication.getName()),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/api/applications/{id}/files")
    public ResponseEntity<List<FileResponse>> getFilesByApplication(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                fileStorageService.getFilesByApplication(id, authentication.getName())
        );
    }

    @DeleteMapping("/api/files/{id}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long id,
            Authentication authentication
    ) {
        fileStorageService.deleteFile(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/files/{id}/access-url")
    public ResponseEntity<FileAccessUrlResponse> getFileAccessUrl(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                fileStorageService.generatePresignedAccessUrl(id, authentication.getName())
        );
    }
}
