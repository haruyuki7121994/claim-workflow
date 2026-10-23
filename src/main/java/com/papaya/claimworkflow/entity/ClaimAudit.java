package com.papaya.claimworkflow.entity;

import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "claim_transition_audit")
@org.hibernate.annotations.Immutable
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimAudit {
    @Id @Column(name = "audit_id", updatable = false) private UUID auditId;
    @Column(name = "claim_id", nullable = false, updatable = false) private UUID claimId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false, updatable = false) private ClaimState fromState;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, updatable = false) private ClaimState toState;
    @Column(name = "actor_id", nullable = false, updatable = false) private String actorId;
    @Column(name = "actor_role", nullable = false, updatable = false) private Role actorRole;
    @Column(name = "sequence_number", nullable = false, updatable = false) private long sequenceNumber;
    @Column(nullable = false, updatable = false, length = 2000) private String reason;
    @Column(name = "workflow_version", nullable = false, updatable = false) private String workflowVersion;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    public ClaimAudit(UUID auditId, UUID claimId, ClaimState fromState, ClaimState toState, String actorId,
                      Role actorRole, long sequenceNumber, String reason, String workflowVersion, Instant occurredAt) {
        this.auditId = auditId; this.claimId = claimId; this.fromState = fromState; this.toState = toState;
        this.actorId = actorId; this.actorRole = actorRole; this.sequenceNumber = sequenceNumber;
        this.reason = reason; this.workflowVersion = workflowVersion; this.occurredAt = occurredAt;
    }
}
