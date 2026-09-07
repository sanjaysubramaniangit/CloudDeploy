package com.clouddeploy.dto;

import com.clouddeploy.entity.TroubleshootingSeverity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TroubleshootingAIOutputDto {

    private String content;
    private String severity;
    private String rootCause;
    private List<String> remediationSteps;
    private List<String> suggestedCommands;

    public boolean isValid() {
        if (content == null || content.trim().isEmpty() || content.length() > 5000) {
            return false;
        }

        if (severity == null || TroubleshootingSeverity.fromString(severity) == null) {
            return false;
        }

        if (rootCause == null || rootCause.trim().isEmpty() || rootCause.length() > 1000) {
            return false;
        }

        if (remediationSteps == null || remediationSteps.isEmpty() || remediationSteps.size() > 10) {
            return false;
        }
        for (String step : remediationSteps) {
            if (step == null || step.trim().isEmpty() || step.length() > 500) {
                return false;
            }
        }

        if (suggestedCommands != null) {
            if (suggestedCommands.size() > 10) {
                return false;
            }
            for (String cmd : suggestedCommands) {
                if (cmd == null || cmd.trim().isEmpty() || cmd.length() > 500) {
                    return false;
                }
            }
        }

        return true;
    }
}
