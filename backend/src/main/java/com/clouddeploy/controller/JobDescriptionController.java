package com.clouddeploy.controller;

import com.clouddeploy.dto.JobDescriptionRequest;
import com.clouddeploy.dto.JobDescriptionResponse;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.User;
import com.clouddeploy.service.JobDescriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobDescriptionController {

    private final JobDescriptionService jobDescriptionService;

    public JobDescriptionController(JobDescriptionService jobDescriptionService) {
        this.jobDescriptionService = jobDescriptionService;
    }

    @PostMapping
    public ResponseEntity<JobDescriptionResponse> createJob(
            @Valid @RequestBody JobDescriptionRequest request,
            @AuthenticationPrincipal User user) {
        JobDescriptionResponse response = jobDescriptionService.createJob(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<JobDescriptionResponse>> listJobs(
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        List<JobDescriptionResponse> responses = jobDescriptionService.listJobs(user, isAdmin);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDescriptionResponse> getJob(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        JobDescriptionResponse response = jobDescriptionService.getJob(id, user, isAdmin);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobDescriptionResponse> updateJob(
            @PathVariable Long id,
            @Valid @RequestBody JobDescriptionRequest request,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        JobDescriptionResponse response = jobDescriptionService.updateJob(id, request, user, isAdmin);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        jobDescriptionService.deleteJob(id, user, isAdmin);
        return ResponseEntity.ok(Map.of("message", "Job description deleted successfully"));
    }
}
