package com.clouddeploy.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TroubleshootingSessionDetailResponse {

    private Long id;
    private String title;
    private Long applicationId;
    private String applicationName;
    private Long deploymentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TroubleshootingMessageDto> messages;
}
