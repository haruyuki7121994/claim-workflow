package com.papaya.claimworkflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ActionType {
    NOTIFY_ASSESSOR_TEAM("notifyAssessorTeam"),
    LOG_ASSESSMENT_START("logAssessmentStart"),
    NOTIFY_MEMBER("notifyMember"),
    CREATE_PAYMENT_REQUEST("createPaymentRequest"),
    NOTIFY_REJECTION("notifyRejection"),
    NOTIFY_MEMBER_WITH_REQUEST_DETAILS("notifyMemberWithRequestDetails"),
    RESET_ASSESSMENT_TIMER("resetAssessmentTimer"),
    TRIGGER_PAYMENT("triggerPayment"),
    NOTIFY_PAYMENT_CONFIRMATION("notifyPaymentConfirmation"),
    ARCHIVE_CLAIM("archiveClaim");

    private final String value;

    ActionType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
