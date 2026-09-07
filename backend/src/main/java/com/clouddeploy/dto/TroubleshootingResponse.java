package com.clouddeploy.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TroubleshootingResponse {

    private Long sessionId;
    private String sessionTitle;
    private Long applicationId;
    private String applicationName;
    private Long deploymentId;
    private TroubleshootingMessageDto message;
}
