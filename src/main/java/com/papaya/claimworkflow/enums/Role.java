package com.papaya.claimworkflow.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum Role {
    DOCUMENT_CLERK("document_clerk"),
    TEAM_LEAD("team_lead"),
    ASSESSOR("assessor"),
    FINANCE("finance"),
    SYSTEM("system");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static Role fromValue(String value) {
        return find(value).orElseThrow(() -> new IllegalArgumentException("Unknown workflow role: " + value));
    }

    public static Optional<Role> find(String value) {
        if (value == null) return Optional.empty();
        return Arrays.stream(values()).filter(role -> role.value.equalsIgnoreCase(value)
                || role.name().equalsIgnoreCase(value)).findFirst();
    }
}
