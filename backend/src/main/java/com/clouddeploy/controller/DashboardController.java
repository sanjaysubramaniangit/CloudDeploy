package com.clouddeploy.controller;

import com.clouddeploy.dto.DashboardStatsResponse;
import com.clouddeploy.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardStatsResponse> getDashboardStats(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getDashboardStats(authentication.getName()));
    }
}
