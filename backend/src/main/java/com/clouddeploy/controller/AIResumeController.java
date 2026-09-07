package com.clouddeploy.controller;

import com.clouddeploy.dto.ResumeAnalysisRequest;
import com.clouddeploy.dto.ResumeAnalysisResponse;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.User;
import com.clouddeploy.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/resume")
public class AIResumeController {

    private final ResumeService resumeService;

    public AIResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ResumeAnalysisResponse> analyzeResume(
            @Valid @RequestBody ResumeAnalysisRequest request,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        ResumeAnalysisResponse response = resumeService.analyzeResume(request.getResumeId(), user, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/analysis")
    public ResponseEntity<ResumeAnalysisResponse> getAnalysis(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        ResumeAnalysisResponse response = resumeService.getAnalysis(id, user, isAdmin);
        return ResponseEntity.ok(response);
    }
}
