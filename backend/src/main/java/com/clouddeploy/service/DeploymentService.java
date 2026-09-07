package com.clouddeploy.service;

import com.clouddeploy.dto.DeploymentRequest;
import com.clouddeploy.dto.DeploymentResponse;
import com.clouddeploy.entity.*;
import com.clouddeploy.exception.AccessDeniedCustomException;
import com.clouddeploy.exception.ResourceNotFoundException;
import com.clouddeploy.repository.ApplicationRepository;
import com.clouddeploy.repository.DeploymentRepository;
import com.clouddeploy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;

    @Transactional
    public DeploymentResponse createDeployment(Long applicationId, DeploymentRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        Application application = applicationService.findApplicationAndVerifyOwnership(applicationId, user);

        Deployment deployment = Deployment.builder()
                .application(application)
                .version(request.getVersion().trim())
                .commitHash(request.getCommitHash() != null ? request.getCommitHash().trim() : null)
                .status(request.getStatus())
                .deploymentMessage(request.getDeploymentMessage())
                .deployedAt(LocalDateTime.now())
                .build();

        Deployment saved = deploymentRepository.save(deployment);

        // Update application's deploymentStatus
        ApplicationStatus newAppStatus = switch (request.getStatus()) {
            case SUCCESS -> ApplicationStatus.SUCCESS;
            case RUNNING -> ApplicationStatus.RUNNING;
            case FAILED -> ApplicationStatus.FAILED;
            case PENDING -> ApplicationStatus.PENDING;
        };
        application.setDeploymentStatus(newAppStatus);
        applicationRepository.save(application);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeploymentsByApplication(Long applicationId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Application application = applicationService.findApplicationAndVerifyOwnership(applicationId, user);

        return deploymentRepository.findByApplicationOrderByDeployedAtDesc(application)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DeploymentResponse getDeploymentById(Long deploymentId, String userEmail) {
        User user = getUserByEmail(userEmail);
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found with id: " + deploymentId));

        Application application = deployment.getApplication();
        if (user.getRole() != Role.ADMIN && !application.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to access deployment with id: " + deploymentId);
        }

        return mapToResponse(deployment);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    public DeploymentResponse mapToResponse(Deployment deployment) {
        return DeploymentResponse.builder()
                .id(deployment.getId())
                .applicationId(deployment.getApplication().getId())
                .applicationName(deployment.getApplication().getName())
                .version(deployment.getVersion())
                .commitHash(deployment.getCommitHash())
                .status(deployment.getStatus())
                .deploymentMessage(deployment.getDeploymentMessage())
                .deployedAt(deployment.getDeployedAt())
                .build();
    }
}
