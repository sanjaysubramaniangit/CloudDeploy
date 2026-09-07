package com.clouddeploy.controller;

import com.clouddeploy.dto.ApplicationRequest;
import com.clouddeploy.dto.ApplicationResponse;
import com.clouddeploy.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> getUserApplications(Authentication authentication) {
        return ResponseEntity.ok(applicationService.getUserApplications(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> createApplication(
            @Valid @RequestBody ApplicationRequest request,
            Authentication authentication
    ) {
        return new ResponseEntity<>(
                applicationService.createApplication(request, authentication.getName()),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplicationById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(applicationService.getApplicationById(id, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(applicationService.updateApplication(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(
            @PathVariable Long id,
            Authentication authentication
    ) {
        applicationService.deleteApplication(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
