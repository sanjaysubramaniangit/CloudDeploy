package com.clouddeploy.entity;

public enum InterviewCategory {
    JAVA("Java"),
    DSA("Data Structures & Algorithms"),
    SPRING_BOOT("Spring Boot"),
    REST_APIS("REST APIs"),
    SQL("SQL"),
    AWS("AWS"),
    DOCKER("Docker"),
    LINUX("Linux"),
    DEVOPS("DevOps"),
    AI("AI"),
    CLOUD_SECURITY("Cloud Security"),
    BEHAVIORAL("Behavioral"),
    PROJECT_SPECIFIC("Project-specific");

    private final String displayName;

    InterviewCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InterviewCategory fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase().replace(" ", "_").replace("-", "_").replace("&", "_");
        for (InterviewCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(normalized) || cat.displayName.equalsIgnoreCase(raw.trim())) {
                return cat;
            }
        }
        if (normalized.contains("ALGORITHM") || normalized.contains("DATA_STRUCTURE") || normalized.equals("DSA")) return DSA;
        if (normalized.contains("SPRING")) return SPRING_BOOT;
        if (normalized.contains("REST") || normalized.contains("API")) return REST_APIS;
        if (normalized.contains("SQL") || normalized.contains("DATABASE")) return SQL;
        if (normalized.contains("AWS") || normalized.contains("CLOUD_ARCH")) return AWS;
        if (normalized.contains("DOCKER") || normalized.contains("CONTAINER")) return DOCKER;
        if (normalized.contains("LINUX") || normalized.contains("UNIX") || normalized.contains("OS")) return LINUX;
        if (normalized.contains("DEVOPS") || normalized.contains("CI_CD") || normalized.contains("CICD") || normalized.contains("KUBERNETES")) return DEVOPS;
        if (normalized.contains("SECURITY")) return CLOUD_SECURITY;
        if (normalized.contains("BEHAVIOR") || normalized.contains("LEADERSHIP") || normalized.contains("SOFT_SKILL")) return BEHAVIORAL;
        if (normalized.contains("PROJECT") || normalized.contains("ARCHITECTURE")) return PROJECT_SPECIFIC;
        if (normalized.contains("JAVA") && !normalized.contains("JAVASCRIPT")) return JAVA;
        if (normalized.contains("AI") || normalized.contains("ML") || normalized.contains("LLM")) return AI;

        return null;
    }
}
