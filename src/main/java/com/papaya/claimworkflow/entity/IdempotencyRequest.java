package com.papaya.claimworkflow.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "idempotency_requests")
@org.hibernate.annotations.Immutable
@IdClass(IdempotencyId.class)
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRequest {
    @Id @Column(name = "tenant_id") private String tenantId;
    @Id @Column(name = "actor_id") private String actorId;
    @Id @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(name = "claim_id", nullable = false) private UUID claimId;
    @Column(name = "request_hash", nullable = false) private String requestHash;
    @Convert(converter = JsonMapConverter.class) @Column(name = "response_body", nullable = false, columnDefinition = "text")
    private Map<String, Object> responseBody;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public IdempotencyRequest(String tenantId, String actorId, String key, UUID claimId,
                              String hash, Map<String, Object> response, Instant now) {
        this.tenantId = tenantId; this.actorId = actorId; this.idempotencyKey = key;
        this.claimId = claimId; this.requestHash = hash; this.responseBody = response; this.createdAt = now;
    }
}
