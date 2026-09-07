package com.clouddeploy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TroubleshootingPromptRequest {

    private Long sessionId;

    private Long applicationId;

    private Long deploymentId;

    @NotBlank(message = "Query cannot be blank")
    @Size(max = 4000, message = "Query cannot exceed 4000 characters")
    private String query;

    @Size(max = 8000, message = "Log snippet cannot exceed 8000 characters")
    private String logSnippet;
}
