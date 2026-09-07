package com.clouddeploy.dto;

import com.clouddeploy.entity.DeploymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeploymentRequest {

    @NotBlank(message = "Version is required")
    @Size(max = 50, message = "Version cannot exceed 50 characters")
    private String version;

    @Size(max = 40, message = "Commit hash cannot exceed 40 characters")
    private String commitHash;

    @NotNull(message = "Status is required")
    private DeploymentStatus status;

    @Size(max = 1000, message = "Deployment message cannot exceed 1000 characters")
    private String deploymentMessage;
}
