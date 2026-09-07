package com.clouddeploy.dto;

import com.clouddeploy.entity.InterviewDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionResponse {
    private Long id;
    private Long resumeId;
    private String resumeFileName;
    private Long jobDescriptionId;
    private String jobTitle;
    private String company;
    private InterviewDifficulty difficulty;
    private int questionCount;
    private List<InterviewQuestionDto> questions;
    private LocalDateTime createdAt;
}
