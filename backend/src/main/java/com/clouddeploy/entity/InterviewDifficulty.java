package com.clouddeploy.entity;

public enum InterviewDifficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED;

    public static InterviewDifficulty fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return InterviewDifficulty.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
