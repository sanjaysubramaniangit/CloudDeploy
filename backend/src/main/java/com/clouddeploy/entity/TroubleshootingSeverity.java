package com.clouddeploy.entity;

public enum TroubleshootingSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO;

    public static TroubleshootingSeverity fromString(String value) {
        if (value == null) {
            return null;
        }
        for (TroubleshootingSeverity s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return null;
    }
}
