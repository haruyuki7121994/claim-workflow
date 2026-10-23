package com.papaya.claimworkflow.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum GuardType {
    DOCUMENTS_COMPLETE("documentsComplete"),
    ASSESSOR_ASSIGNED("assessorAssigned"),
    ASSESSMENT_COMPLETE("assessmentComplete"),
    AMOUNT_WITHIN_POLICY_LIMIT("amountWithinPolicyLimit"),
    REJECTION_REASON_PROVIDED("rejectionReasonProvided"),
    MISSING_INFO_DESCRIPTION_PROVIDED("missingInfoDescriptionProvided"),
    NEW_INFORMATION_RECEIVED("newInformationReceived"),
    PAYMENT_REQUEST_CREATED("paymentRequestCreated"),
    PAYMENT_CONFIRMED("paymentConfirmed"),
    APPEAL_EXPIRED_OR_ACKNOWLEDGED("appealExpiredOrAcknowledged");

    private final String value;

    GuardType(String value) { this.value = value; }

    @JsonValue
    public String value() { return value; }

    @JsonCreator
    public static GuardType fromValue(String value) {
        return find(value).orElseThrow(() -> new IllegalArgumentException("Unknown guard: " + value));
    }

    public static Optional<GuardType> find(String value) {
        if (value == null) return Optional.empty();
        return Arrays.stream(values()).filter(type -> type.value.equalsIgnoreCase(value)
                || type.name().equalsIgnoreCase(value)).findFirst();
    }
}
