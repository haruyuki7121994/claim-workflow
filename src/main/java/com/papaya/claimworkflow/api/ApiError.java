package com.papaya.claimworkflow.api;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details) {
    public ApiError {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of());
    }
}
