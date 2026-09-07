package com.clouddeploy.dto;

import com.clouddeploy.entity.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationResponse {
    private Long id;
    private String name;
    private String description;
    private String repositoryUrl;
    private ApplicationStatus deploymentStatus;
    private Long ownerId;
    private String ownerEmail;
    private String ownerName;
    private int totalDeployments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
