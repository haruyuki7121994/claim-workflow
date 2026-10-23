package com.papaya.claimworkflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.papaya.claimworkflow.enums.ClaimState;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

class ApiExceptionHandlerTests {
    private final ApiExceptionHandler advice = new ApiExceptionHandler();

    @Test
    void explainsMissingMockEndpointWithoutReturningInternalServerError() {
        var exception = new NoResourceFoundException(HttpMethod.POST,
                "api/v1/mock/complete-assessment/claim-1", "/api/v1/mock/complete-assessment/claim-1");
        var response = advice.missingEndpoint(exception);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ENDPOINT_NOT_FOUND");
        assertThat(response.getBody().message()).contains("local profile");
    }

    @Test
    void reportsMissingResourceAsNotFound() {
        var exception = new NoResourceFoundException(HttpMethod.GET, "missing-page", "/missing-page");
        var response = advice.missingEndpoint(exception);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Requested endpoint or resource does not exist");
    }

    @Test
    void preservesDocumentedWorkflowError() {
        var response = advice.workflow(new WorkflowException(409, "INVALID_TRANSITION",
                "Transition SUBMITTED -> APPROVED is not allowed",
                Map.of("allowedTransitions", java.util.List.of(ClaimState.DOCUMENTS_VERIFIED))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiError("INVALID_TRANSITION",
                "Transition SUBMITTED -> APPROVED is not allowed",
                Map.of("allowedTransitions", java.util.List.of(ClaimState.DOCUMENTS_VERIFIED))));
    }

    @Test
    void reportsAllowedEnumValuesForInvalidState() {
        InvalidFormatException cause = InvalidFormatException.from(null, "Invalid state", "UNKNOWN", ClaimState.class);
        cause.prependPath(this, "toState");
        var exception = new HttpMessageNotReadableException("Invalid request", cause,
                new MockHttpInputMessage(new byte[0]));

        var response = advice.unreadable(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().details()).containsEntry("field", "toState")
                .containsEntry("rejectedValue", "UNKNOWN");
        assertThat(response.getBody().details().get("allowedValues").toString())
                .contains(ClaimState.SUBMITTED.name(), ClaimState.CLOSED.name());
    }
}
