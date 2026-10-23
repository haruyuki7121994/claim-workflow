package com.papaya.claimworkflow.api;

import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.dto.MockConditionRequests.*;
import com.papaya.claimworkflow.dto.Requests;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.Role;
import com.papaya.claimworkflow.service.OutboxWorker;
import com.papaya.claimworkflow.service.WorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Local simulation: all evidence writes use the normal role, state and version checks. */
@RestController
@Profile({"local", "test"})
@RequestMapping("/api/v1/mock")
@RequiredArgsConstructor
public class MockConditionController {
    private final WorkflowService workflow;
    private final OutboxWorker worker;
    private final java.time.Clock clock;

    @PostMapping("/upload-documents/{claimId}")
    public Claim uploadDocuments(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody Version request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("documentsComplete", true));
    }

    @PostMapping("/assign-assessor/{claimId}")
    public Claim assignAssessor(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody Assignee request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("assignedAssessorId", request.assessorId()));
    }

    @PostMapping("/complete-assessment/{claimId}")
    public Claim completeAssessment(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody Version request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("assessmentComplete", true));
    }

    @PostMapping("/rejection-reason/{claimId}")
    public Claim rejectionReason(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody Description request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("rejectionReason", request.description()));
    }

    @PostMapping("/missing-information/{claimId}")
    public Claim missingInformation(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody Description request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("missingInfoDescription", request.description()));
    }

    @PostMapping("/receive-information/{claimId}")
    public Claim receiveInformation(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody Version request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("newInformationReceived", true));
    }

    @PostMapping("/confirm-payment/{claimId}")
    public Claim confirmPayment(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody Payment request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("paymentReference", request.paymentReference()));
    }

    @PostMapping("/acknowledge-rejection/{claimId}")
    public Claim acknowledgeRejection(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody Version request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("memberAcknowledged", true));
    }

    @PostMapping("/expire-appeal/{claimId}")
    public Claim expireAppeal(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody Version request) {
        return update(claimId, jwt, request.expectedVersion(), Map.of("appealDeadline", clock.instant().toString()));
    }

    // Execute the real mock outbox strategies after their transition transaction has committed.
    @PostMapping({"/create-payment-request/{claimId}", "/process-payment/{claimId}"})
    public List<Map<String, Object>> processPayment(@PathVariable UUID claimId, @AuthenticationPrincipal Jwt jwt) {
        Actor actor = Actor.from(jwt);
        Claim claim = workflow.get(claimId, actor);
        if (!actor.has(Role.FINANCE)) {
            throw new WorkflowException(403, "FORBIDDEN", "Payment simulation requires finance role");
        }
        if (claim.state() != ClaimState.APPROVED && claim.state() != ClaimState.PAYMENT_INITIATED) {
            throw new WorkflowException(409, "PRECONDITION_FAILED", "Claim must be approved or payment initiated");
        }
        for (int count = 0; count < 100 && worker.processOne(claimId); count++) { }
        return workflow.effects(claimId, actor, 200, 0);
    }

    private Claim update(UUID id, Jwt jwt, long version, Map<String, Object> facts) {
        return workflow.evidence(id, Actor.from(jwt), new Requests.Evidence(version, facts));
    }
}
