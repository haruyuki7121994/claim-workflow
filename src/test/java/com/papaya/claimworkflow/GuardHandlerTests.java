package com.papaya.claimworkflow;

import static org.assertj.core.api.Assertions.*;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.GuardType;
import com.papaya.claimworkflow.repository.OutboxRepository;
import com.papaya.claimworkflow.service.GuardRegistry;
import com.papaya.claimworkflow.service.guard.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuardHandlerTests {
    private final Instant now = Instant.parse("2026-10-23T00:00:00Z");

    private Claim claim(Map<String, Object> facts) {
        return new Claim(UUID.randomUUID(), "tenant-a", ClaimState.SUBMITTED, "1", facts, now);
    }

    @Test
    void documentsAreRequiredBeforeVerification() {
        var guard = new DocumentsCompletedHandler();
        assertThat(guard.test(claim(Map.of()))).isFalse();
        assertThat(guard.test(claim(Map.of("documentsComplete", false)))).isFalse();
        assertThat(guard.test(claim(Map.of("documentsComplete", true)))).isTrue();
    }

    @Test
    void assignmentRequiresNonBlankAssessor() {
        var guard = new AssessorAssignedHandler();
        assertThat(guard.test(claim(Map.of("assignedAssessorId", " ")))).isFalse();
        assertThat(guard.test(claim(Map.of("assignedAssessorId", "assessor-1")))).isTrue();
    }

    @Test
    void assessmentRequiresCompletionFlag() {
        var guard = new AssessmentCompleteHandler();
        assertThat(guard.test(claim(Map.of()))).isFalse();
        assertThat(guard.test(claim(Map.of("assessmentComplete", true)))).isTrue();
    }

    @Test
    void policyLimitAllowsEqualityAndRejectsExcess() {
        var guard = new AmountWithinPolicyLimitHandler();
        assertThat(guard.test(claim(Map.of("amount", "1000.00", "policyLimit", "1000.00")))).isTrue();
        assertThat(guard.test(claim(Map.of("amount", "1000.01", "policyLimit", "1000.00")))).isFalse();
    }

    @Test
    void rejectionRequiresReason() {
        var guard = new RejectionReasonProvidedHandler();
        assertThat(guard.test(claim(Map.of("rejectionReason", " ")))).isFalse();
        assertThat(guard.test(claim(Map.of("rejectionReason", "Excluded")))).isTrue();
    }

    @Test
    void informationRequestRequiresDescription() {
        var guard = new MissingInfoDescriptionProvidedHandler();
        assertThat(guard.test(claim(Map.of()))).isFalse();
        assertThat(guard.test(claim(Map.of("missingInfoDescription", "Medical report")))).isTrue();
    }

    @Test
    void newInformationRequiresReceiptFlag() {
        var guard = new NewInformationReceivedHandler();
        assertThat(guard.test(claim(Map.of()))).isFalse();
        assertThat(guard.test(claim(Map.of("newInformationReceived", true)))).isTrue();
    }

    @Test
    void rejectionCanCloseOnlyAfterDeadlineOrAcknowledgement() {
        var guard = new AppealExpiredOrAcknowledgedHandler(Clock.fixed(now, ZoneOffset.UTC));
        assertThat(guard.test(claim(Map.of()))).isFalse();
        assertThat(guard.test(claim(Map.of("appealDeadline", now.plusSeconds(1).toString())))).isFalse();
        assertThat(guard.test(claim(Map.of("appealDeadline", now.toString())))).isTrue();
        assertThat(guard.test(claim(Map.of("memberAcknowledged", true)))).isTrue();
    }

    @Test
    void paymentRequestRequiresCompletedOutboxReceipt() {
        assertThat(new PaymentRequestCreatedHandler(outbox(false)).test(claim(Map.of()))).isFalse();
        assertThat(new PaymentRequestCreatedHandler(outbox(true)).test(claim(Map.of()))).isTrue();
    }

    @Test
    void paymentConfirmationRequiresBothReferenceAndCompletedTrigger() {
        Claim confirmed = claim(Map.of("paymentReference", "PAY-123"));
        assertThat(new PaymentConfirmedHandler(outbox(false)).test(confirmed)).isFalse();
        assertThat(new PaymentConfirmedHandler(outbox(true)).test(claim(Map.of()))).isFalse();
        assertThat(new PaymentConfirmedHandler(outbox(true)).test(confirmed)).isTrue();
    }

    @Test
    void registryDispatchesStrategyAndRejectsDuplicates() {
        var guard = new DocumentsCompletedHandler();
        var registry = new GuardRegistry(List.of(guard));
        assertThat(registry.passes(GuardType.DOCUMENTS_COMPLETE.value(), claim(Map.of()))).isFalse();
        assertThatThrownBy(() -> new GuardRegistry(List.of(guard, guard)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate guard");
    }

    private OutboxRepository outbox(boolean completed) {
        return new OutboxRepository(null, null, null) {
            @Override
            public boolean completed(UUID id, ActionType action) {
                return completed;
            }
        };
    }
}
