package com.clouddeploy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationRequest {

    @NotBlank(message = "Application name is required")
    @Size(min = 2, max = 100, message = "Application name must be between 2 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Pattern(regexp = "^$|^(https?://.+|git@.+)$", message = "Repository URL must be a valid HTTP/HTTPS or Git SSH URL")
    private String repositoryUrl;
}
