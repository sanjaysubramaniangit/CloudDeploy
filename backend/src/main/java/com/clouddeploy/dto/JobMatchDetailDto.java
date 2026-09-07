package com.clouddeploy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchDetailDto {
    private Long id;
    private String skill;
    private String category;
    private boolean required;
    private boolean matched;
    private String matchType;
    private Double confidence;
    private String evidence;
    private String recommendation;
}
