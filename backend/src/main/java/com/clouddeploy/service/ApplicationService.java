package com.clouddeploy.service;

import com.clouddeploy.dto.ApplicationRequest;
import com.clouddeploy.dto.ApplicationResponse;
import com.clouddeploy.entity.Application;
import com.clouddeploy.entity.ApplicationStatus;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.User;
import com.clouddeploy.exception.AccessDeniedCustomException;
import com.clouddeploy.exception.ResourceNotFoundException;
import com.clouddeploy.repository.ApplicationRepository;
import com.clouddeploy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @Transactional
    public ApplicationResponse createApplication(ApplicationRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        Application application = Application.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .repositoryUrl(request.getRepositoryUrl())
                .deploymentStatus(ApplicationStatus.OFFLINE)
                .owner(user)
                .build();

        Application saved = applicationRepository.save(application);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getUserApplications(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<Application> applications;

        if (user.getRole() == Role.ADMIN) {
            applications = applicationRepository.findAllByOrderByUpdatedAtDesc();
        } else {
            applications = applicationRepository.findByOwnerOrderByUpdatedAtDesc(user);
        }

        return applications.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplicationById(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        Application application = findApplicationAndVerifyOwnership(id, user);
        return mapToResponse(application);
    }

    @Transactional
    public ApplicationResponse updateApplication(Long id, ApplicationRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);
        Application application = findApplicationAndVerifyOwnership(id, user);

        application.setName(request.getName().trim());
        application.setDescription(request.getDescription());
        application.setRepositoryUrl(request.getRepositoryUrl());

        Application updated = applicationRepository.save(application);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteApplication(Long id, String userEmail) {
        User user = getUserByEmail(userEmail);
        Application application = findApplicationAndVerifyOwnership(id, user);
        applicationRepository.delete(application);
    }

    public Application findApplicationAndVerifyOwnership(Long id, User user) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));

        if (user.getRole() != Role.ADMIN && !application.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedCustomException("You do not have permission to access application with id: " + id);
        }

        return application;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    public ApplicationResponse mapToResponse(Application app) {
        return ApplicationResponse.builder()
                .id(app.getId())
                .name(app.getName())
                .description(app.getDescription())
                .repositoryUrl(app.getRepositoryUrl())
                .deploymentStatus(app.getDeploymentStatus())
                .ownerId(app.getOwner().getId())
                .ownerEmail(app.getOwner().getEmail())
                .ownerName(app.getOwner().getName())
                .totalDeployments(app.getDeployments() != null ? app.getDeployments().size() : 0)
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
