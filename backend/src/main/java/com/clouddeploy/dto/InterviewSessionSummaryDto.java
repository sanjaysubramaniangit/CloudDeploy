package com.clouddeploy.dto;

import com.clouddeploy.entity.InterviewDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionSummaryDto {
    private Long id;
    private Long resumeId;
    private String resumeFileName;
    private Long jobDescriptionId;
    private String jobTitle;
    private String company;
    private InterviewDifficulty difficulty;
    private int questionCount;
    private LocalDateTime createdAt;
}
