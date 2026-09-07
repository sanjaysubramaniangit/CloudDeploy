package com.clouddeploy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDescriptionRequest {

    @NotBlank(message = "Job title is required")
    @Size(max = 255, message = "Job title must not exceed 255 characters")
    private String title;

    @Size(max = 255, message = "Company name must not exceed 255 characters")
    private String company;

    @NotBlank(message = "Job description is required")
    @Size(max = 50000, message = "Job description is too long (maximum 50,000 characters)")
    private String description;

    @Size(max = 500, message = "Source URL must not exceed 500 characters")
    private String sourceUrl;
}
