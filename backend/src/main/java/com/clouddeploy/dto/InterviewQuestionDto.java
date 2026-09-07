package com.clouddeploy.dto;

import com.clouddeploy.entity.InterviewCategory;
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
public class InterviewQuestionDto {
    private Long id;
    private String question;
    private InterviewCategory category;
    private String categoryDisplayName;
    private InterviewDifficulty difficulty;
    private List<String> expectedConcepts;
    private LocalDateTime createdAt;
}
