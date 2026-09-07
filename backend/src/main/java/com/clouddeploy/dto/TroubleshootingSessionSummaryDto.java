package com.clouddeploy.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TroubleshootingSessionSummaryDto {

    private Long id;
    private String title;
    private Long applicationId;
    private String applicationName;
    private long messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
