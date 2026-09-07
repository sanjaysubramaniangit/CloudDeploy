package com.clouddeploy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeAnalysisDto {
    private String summary;
    
    @Builder.Default
    private List<String> technicalSkills = new ArrayList<>();
    
    @Builder.Default
    private List<String> programmingLanguages = new ArrayList<>();
    
    @Builder.Default
    private List<String> frameworks = new ArrayList<>();
    
    @Builder.Default
    private List<String> cloudTechnologies = new ArrayList<>();
    
    @Builder.Default
    private List<String> databases = new ArrayList<>();
    
    @Builder.Default
    private List<String> devopsTools = new ArrayList<>();
    
    @Builder.Default
    private List<String> experienceHighlights = new ArrayList<>();
    
    @Builder.Default
    private List<String> strengths = new ArrayList<>();
    
    @Builder.Default
    private List<String> areasToImprove = new ArrayList<>();
    
    @Builder.Default
    private List<String> recommendedSkills = new ArrayList<>();

    public boolean isValid() {
        return summary != null && !summary.trim().isEmpty();
    }
}
