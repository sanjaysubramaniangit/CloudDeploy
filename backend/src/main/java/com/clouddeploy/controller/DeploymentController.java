package com.clouddeploy.controller;

import com.clouddeploy.dto.DeploymentRequest;
import com.clouddeploy.dto.DeploymentResponse;
import com.clouddeploy.service.DeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @GetMapping("/api/applications/{applicationId}/deployments")
    public ResponseEntity<List<DeploymentResponse>> getDeploymentsByApplication(
            @PathVariable Long applicationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                deploymentService.getDeploymentsByApplication(applicationId, authentication.getName())
        );
    }

    @PostMapping("/api/applications/{applicationId}/deployments")
    public ResponseEntity<DeploymentResponse> createDeployment(
            @PathVariable Long applicationId,
            @Valid @RequestBody DeploymentRequest request,
            Authentication authentication
    ) {
        return new ResponseEntity<>(
                deploymentService.createDeployment(applicationId, request, authentication.getName()),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/api/deployments/{id}")
    public ResponseEntity<DeploymentResponse> getDeploymentById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                deploymentService.getDeploymentById(id, authentication.getName())
        );
    }
}
