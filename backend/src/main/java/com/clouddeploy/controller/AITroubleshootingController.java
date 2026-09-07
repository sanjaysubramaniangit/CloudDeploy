package com.clouddeploy.controller;

import com.clouddeploy.dto.*;
import com.clouddeploy.entity.User;
import com.clouddeploy.service.TroubleshootingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/assistant")
public class AITroubleshootingController {

    private final TroubleshootingService troubleshootingService;

    public AITroubleshootingController(TroubleshootingService troubleshootingService) {
        this.troubleshootingService = troubleshootingService;
    }

    @PostMapping("/chat")
    public ResponseEntity<TroubleshootingResponse> chat(
            @Valid @RequestBody TroubleshootingPromptRequest request,
            @AuthenticationPrincipal User user) {
        TroubleshootingResponse response = troubleshootingService.processChatTurn(request, user.getEmail());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<TroubleshootingSessionSummaryDto>> listSessions(
            @AuthenticationPrincipal User user) {
        List<TroubleshootingSessionSummaryDto> responses = troubleshootingService.getUserSessions(user.getEmail());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<TroubleshootingSessionDetailResponse> getSession(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        TroubleshootingSessionDetailResponse response = troubleshootingService.getSessionById(id, user.getEmail());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Map<String, String>> deleteSession(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        troubleshootingService.deleteSession(id, user.getEmail());
        return ResponseEntity.ok(Map.of("message", "Troubleshooting session deleted successfully"));
    }
}
