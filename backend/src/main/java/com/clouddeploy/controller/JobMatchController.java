package com.clouddeploy.controller;

import com.clouddeploy.dto.JobMatchRequest;
import com.clouddeploy.dto.JobMatchResponse;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.User;
import com.clouddeploy.service.JobMatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/job-match")
public class JobMatchController {

    private final JobMatchService jobMatchService;

    public JobMatchController(JobMatchService jobMatchService) {
        this.jobMatchService = jobMatchService;
    }

    @PostMapping
    public ResponseEntity<JobMatchResponse> matchJob(
            @Valid @RequestBody JobMatchRequest request,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        JobMatchResponse response = jobMatchService.matchJob(request, user, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobMatchResponse> getMatch(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        JobMatchResponse response = jobMatchService.getMatch(id, user, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<JobMatchResponse>> listMatches(
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        List<JobMatchResponse> responses = jobMatchService.listMatchesForUser(user, isAdmin);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/resume/{resumeId}")
    public ResponseEntity<List<JobMatchResponse>> listMatchesForResume(
            @PathVariable Long resumeId,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        List<JobMatchResponse> responses = jobMatchService.listMatchesForResume(resumeId, user, isAdmin);
        return ResponseEntity.ok(responses);
    }
}
