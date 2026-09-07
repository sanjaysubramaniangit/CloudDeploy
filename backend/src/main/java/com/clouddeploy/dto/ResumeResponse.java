package com.clouddeploy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeResponse {
    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String extractedSnippet;
    private boolean hasAnalysis;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
