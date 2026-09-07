package com.clouddeploy.controller;

import com.clouddeploy.dto.InterviewGenerationRequest;
import com.clouddeploy.dto.InterviewSessionResponse;
import com.clouddeploy.dto.InterviewSessionSummaryDto;
import com.clouddeploy.entity.Role;
import com.clouddeploy.entity.User;
import com.clouddeploy.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/interview")
public class AIInterviewController {

    private final InterviewService interviewService;

    public AIInterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/generate")
    public ResponseEntity<InterviewSessionResponse> generateInterview(
            @Valid @RequestBody InterviewGenerationRequest request,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        InterviewSessionResponse response = interviewService.generateInterview(request, user, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewSessionResponse> getSession(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        InterviewSessionResponse response = interviewService.getSession(id, user, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<InterviewSessionSummaryDto>> listSessions(
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        List<InterviewSessionSummaryDto> responses = interviewService.listUserSessions(user, isAdmin);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteSession(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        boolean isAdmin = user != null && user.getRole() == Role.ADMIN;
        interviewService.deleteSession(id, user, isAdmin);
        return ResponseEntity.ok(Map.of("message", "Interview session deleted successfully"));
    }
}
