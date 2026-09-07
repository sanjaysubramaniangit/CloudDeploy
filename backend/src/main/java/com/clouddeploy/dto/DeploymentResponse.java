package com.clouddeploy.dto;

import com.clouddeploy.entity.DeploymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeploymentResponse {
    private Long id;
    private Long applicationId;
    private String applicationName;
    private String version;
    private String commitHash;
    private DeploymentStatus status;
    private String deploymentMessage;
    private LocalDateTime deployedAt;
}
