package com.clouddeploy.entity;

public enum TroubleshootingMessageRole {
    USER,
    ASSISTANT,
    SYSTEM;

    public static TroubleshootingMessageRole fromString(String value) {
        if (value == null) {
            return null;
        }
        for (TroubleshootingMessageRole r : values()) {
            if (r.name().equalsIgnoreCase(value.trim())) {
                return r;
            }
        }
        return null;
    }
}
