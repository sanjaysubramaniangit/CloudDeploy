package com.clouddeploy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardStatsResponse {
    private long totalApplications;
    private long totalDeployments;
    private long successfulDeployments;
    private long failedDeployments;
    private List<ApplicationResponse> recentApplications;
    private List<DeploymentResponse> recentDeployments;
}
