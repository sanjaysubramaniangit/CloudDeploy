package com.clouddeploy.dto;

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
public class JobMatchResponse {
    private Long id;
    private Long resumeId;
    private String resumeFileName;
    private Long jobDescriptionId;
    private String jobTitle;
    private String company;
    private Integer overallScore;
    private Integer technicalScore;
    private Integer cloudScore;
    private Integer devopsScore;
    private Integer programmingScore;
    private Integer backendScore;
    private Integer databaseScore;
    private Integer experienceScore;
    private String summary;
    private List<String> strengths;
    private List<String> missingSkills;
    private List<String> recommendations;
    private List<String> preparationAreas;
    private List<JobMatchDetailDto> details;
    private String aiProvider;
    private String aiModel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
