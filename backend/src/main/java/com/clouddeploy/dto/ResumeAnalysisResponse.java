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
public class ResumeAnalysisResponse {
    private Long id;
    private Long resumeId;
    private String fileName;
    private String summary;
    private List<String> technicalSkills;
    private List<String> programmingLanguages;
    private List<String> frameworks;
    private List<String> cloudTechnologies;
    private List<String> databases;
    private List<String> devopsTools;
    private List<String> experienceHighlights;
    private List<String> strengths;
    private List<String> areasToImprove;
    private List<String> recommendedSkills;
    private String rawJson;
    private String aiProvider;
    private String aiModel;
    private LocalDateTime analyzedAt;
}
