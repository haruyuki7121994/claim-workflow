package com.papaya.claimworkflow.api;

import java.util.Map;

public class WorkflowException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, Object> details;

    public WorkflowException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public WorkflowException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int status() { return status; }
    public String code() { return code; }
    public Map<String, Object> details() { return details; }

    public static WorkflowException conflict() {
        return new WorkflowException(409, "CONCURRENT_MODIFICATION", "Claim changed; reload and retry with its current version");
    }
}
