package com.clouddeploy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileAccessUrlResponse {
    private Long fileId;
    private String fileName;
    private String accessUrl;
    private LocalDateTime expiresAt;
}
