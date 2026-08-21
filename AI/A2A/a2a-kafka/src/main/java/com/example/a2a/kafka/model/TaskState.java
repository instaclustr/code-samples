package com.example.a2a.kafka.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A2A Task lifecycle state (Agent2Agent Protocol).
 */
public enum TaskState {
    SUBMITTED("submitted"),
    WORKING("working"),
    INPUT_REQUIRED("input-required"),
    COMPLETED("completed"),
    CANCELED("canceled"),
    FAILED("failed"),
    REJECTED("rejected"),
    AUTH_REQUIRED("auth-required"),
    UNKNOWN("unknown");

    private final String value;

    TaskState(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TaskState fromValue(String v) {
        for (TaskState s : values()) {
            if (s.value.equals(v)) return s;
        }
        return UNKNOWN;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED || this == FAILED || this == REJECTED;
    }
}
