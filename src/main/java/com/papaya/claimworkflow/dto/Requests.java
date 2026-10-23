package com.papaya.claimworkflow.dto;

import com.papaya.claimworkflow.enums.ClaimState;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public final class Requests {
    private Requests() { }

    public record CreateClaim(@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
                              @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal policyLimit) { }

    public record Transition(@NotNull ClaimState toState,
                             @NotNull @Min(0) Long expectedVersion,
                             @NotBlank @Size(max = 2000) String reason) { }

    public record Evidence(@NotNull @Min(0) Long expectedVersion,
                           @NotEmpty Map<String, Object> facts) { }

    public record LoginRequest(@NotBlank @Size(max = 100) String tenantId,
                              @NotBlank @Size(max = 100) String subject) { }
}
