package com.papaya.claimworkflow.entity;

import com.papaya.claimworkflow.enums.ActionType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "outbox_events")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "claim_id", nullable = false) private UUID claimId;
    @Column(name = "transition_id", nullable = false) private UUID transitionId;
    @Column(name = "action_type", nullable = false) private ActionType actionType;
    @Column(nullable = false) private String status;
    @Convert(converter = JsonMapConverter.class) @Column(nullable = false, columnDefinition = "text")
    private Map<String, Object> payload;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "next_retry_at", nullable = false) private Instant nextRetryAt;
    @Column(name = "lease_token") private UUID leaseToken;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "last_error") private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "processed_at") private Instant processedAt;

    public OutboxEvent(UUID eventId, UUID claimId, UUID transitionId, ActionType actionType,
                       Map<String, Object> payload, Instant now) {
        this.eventId = eventId; this.claimId = claimId; this.transitionId = transitionId;
        this.actionType = actionType; this.payload = payload; this.status = "PENDING";
        this.nextRetryAt = now; this.createdAt = now;
    }

    public void claim(UUID token, Instant until) { status = "PROCESSING"; leaseToken = token; leaseUntil = until; retryCount++; }
    public void succeed(Instant now) { status = "COMPLETED"; processedAt = now; lastError = null; leaseToken = null; leaseUntil = null; }
    public void fail(String error, Instant retryAt, boolean terminal) {
        status = terminal ? "FAILED" : "FAILED_RETRYABLE"; lastError = error;
        nextRetryAt = retryAt; leaseToken = null; leaseUntil = null;
    }
    public void expireFinalLease(String error) { status = "FAILED"; lastError = error; leaseToken = null; leaseUntil = null; }
}
