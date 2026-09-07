package com.clouddeploy.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TroubleshootingMessageDto {

    private Long id;
    private String role;
    private String content;
    private String severity;
    private String rootCause;
    private List<String> remediationSteps;
    private List<String> suggestedCommands;
    private LocalDateTime createdAt;
}
