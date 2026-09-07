package com.clouddeploy.service;

import com.clouddeploy.dto.DashboardStatsResponse;
import com.clouddeploy.entity.DeploymentStatus;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.User;
import com.clouddeploy.repository.ApplicationRepository;
import com.clouddeploy.repository.DeploymentRepository;
import com.clouddeploy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ApplicationRepository applicationRepository;
    private final DeploymentRepository deploymentRepository;
    private final ApplicationService applicationService;
    private final DeploymentService deploymentService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        long totalApps;
        long totalDeps;
        long successDeps;
        long failedDeps;
        var recentApps = user.getRole() == Role.ADMIN
                ? applicationRepository.findTop5ByOrderByUpdatedAtDesc()
                : applicationRepository.findTop5ByOwnerOrderByUpdatedAtDesc(user);

        var recentDeps = user.getRole() == Role.ADMIN
                ? deploymentRepository.findTop5ByOrderByDeployedAtDesc()
                : deploymentRepository.findTop5ByApplicationOwnerOrderByDeployedAtDesc(user);

        if (user.getRole() == Role.ADMIN) {
            totalApps = applicationRepository.count();
            totalDeps = deploymentRepository.count();
            successDeps = deploymentRepository.countByStatus(DeploymentStatus.SUCCESS);
            failedDeps = deploymentRepository.countByStatus(DeploymentStatus.FAILED);
        } else {
            totalApps = applicationRepository.countByOwner(user);
            totalDeps = deploymentRepository.countByApplicationOwner(user);
            successDeps = deploymentRepository.countByApplicationOwnerAndStatus(user, DeploymentStatus.SUCCESS);
            failedDeps = deploymentRepository.countByApplicationOwnerAndStatus(user, DeploymentStatus.FAILED);
        }

        return DashboardStatsResponse.builder()
                .totalApplications(totalApps)
                .totalDeployments(totalDeps)
                .successfulDeployments(successDeps)
                .failedDeployments(failedDeps)
                .recentApplications(recentApps.stream().map(applicationService::mapToResponse).collect(Collectors.toList()))
                .recentDeployments(recentDeps.stream().map(deploymentService::mapToResponse).collect(Collectors.toList()))
                .build();
    }
}
