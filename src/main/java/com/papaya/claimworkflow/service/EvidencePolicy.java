package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.api.WorkflowException;
import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.Role;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EvidencePolicy {
    private record Permission(Role role, Set<ClaimState> states, boolean flag, int maxLength) { }
    private final Map<String, Permission> permissions = Map.of(
            "documentsComplete", new Permission(Role.DOCUMENT_CLERK, Set.of(ClaimState.SUBMITTED), true, 0),
            "newInformationReceived", new Permission(Role.DOCUMENT_CLERK, Set.of(ClaimState.PENDING_INFO), true, 0),
            "assignedAssessorId", new Permission(Role.TEAM_LEAD, Set.of(ClaimState.DOCUMENTS_VERIFIED), false, 100),
            "assessmentComplete", new Permission(Role.ASSESSOR, Set.of(ClaimState.UNDER_ASSESSMENT), true, 0),
            "rejectionReason", new Permission(Role.ASSESSOR, Set.of(ClaimState.UNDER_ASSESSMENT), false, 2000),
            "missingInfoDescription", new Permission(Role.ASSESSOR, Set.of(ClaimState.UNDER_ASSESSMENT), false, 2000),
            "paymentReference", new Permission(Role.FINANCE, Set.of(ClaimState.PAYMENT_INITIATED), false, 100),
            "memberAcknowledged", new Permission(Role.SYSTEM, Set.of(ClaimState.REJECTED), true, 0),
            "appealDeadline", new Permission(Role.SYSTEM, Set.of(ClaimState.REJECTED), false, 40)
    );

    public void validate(Claim claim, Actor actor, Map<String, Object> facts) {
        facts.forEach((key, value) -> {
            Permission permission = permissions.get(key);
            if (permission == null) {
                throw new WorkflowException(400, "INVALID_REQUEST", "Evidence field is not writable: " + key);
            }
            if (!actor.has(permission.role()) || !permission.states().contains(claim.state())
                    || (permission.role() == Role.ASSESSOR && !actor.id().equals(claim.text("assignedAssessorId")))) {
                throw new WorkflowException(403, "UNAUTHORIZED_EVIDENCE", "Cannot update " + key + " in state " + claim.state());
            }
            boolean valid = permission.flag() ? value instanceof Boolean : value instanceof String text
                    && !text.isBlank() && text.length() <= permission.maxLength();
            if (valid && key.equals("appealDeadline")) {
                try { java.time.Instant.parse((String) value); }
                catch (java.time.format.DateTimeParseException exception) { valid = false; }
            }
            if (!valid) { throw new WorkflowException(400, "INVALID_REQUEST", "Invalid value for " + key); }
        });
    }
}
