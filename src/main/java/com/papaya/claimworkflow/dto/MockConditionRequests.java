package com.papaya.claimworkflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MockConditionRequests {
    private MockConditionRequests() { }

    public record Version(@NotNull @Min(0) Long expectedVersion) { }
    public record Assignee(@NotNull @Min(0) Long expectedVersion,
                           @NotBlank @Size(max = 100) String assessorId) { }
    public record Description(@NotNull @Min(0) Long expectedVersion,
                              @NotBlank @Size(max = 2000) String description) { }
    public record Payment(@NotNull @Min(0) Long expectedVersion,
                          @NotBlank @Size(max = 100) String paymentReference) { }
}
