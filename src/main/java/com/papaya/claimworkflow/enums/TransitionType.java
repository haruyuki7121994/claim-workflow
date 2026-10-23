package com.papaya.claimworkflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TransitionType {
    VERIFY_DOCUMENTS("verify-documents"),
    START_ASSESSMENT("start-assessment"),
    APPROVE("approve"),
    REJECT("reject"),
    REQUEST_MORE_INFORMATION("request-more-information"),
    RECEIVE_INFORMATION("receive-information"),
    INITIATE_PAYMENT("initiate-payment"),
    CLOSE_PAID("close-paid"),
    CLOSE_REJECTED("close-rejected");

    private final String value;

    TransitionType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
