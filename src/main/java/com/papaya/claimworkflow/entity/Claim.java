package com.papaya.claimworkflow.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.papaya.claimworkflow.enums.ClaimState;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Entity
@Table(name = "claims")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Claim {
    @Id
    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 100)
    private String tenantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private ClaimState state;
    @Column(name = "workflow_version", nullable = false, updatable = false, length = 40)
    private String workflowVersion;
    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "information_request_count", nullable = false)
    private int informationRequestCount;
    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private Map<String, Object> facts = new HashMap<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Claim(UUID claimId, String tenantId, ClaimState state, String workflowVersion,
                 Map<String, Object> facts, Instant now) {
        this.claimId = claimId;
        this.tenantId = tenantId;
        this.state = state;
        this.workflowVersion = workflowVersion;
        this.facts = new HashMap<>(facts);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String text(String key) {
        Object value = facts.get(key);
        return value instanceof String text ? text : "";
    }

    public boolean flag(String key) {
        return Boolean.TRUE.equals(facts.get(key));
    }

    public void applyEvidence(Map<String, Object> updates, Instant now) {
        facts.putAll(updates);
        updatedAt = now;
    }

    public void transitionTo(ClaimState target, int requestCount, Map<String, Object> nextFacts, Instant now) {
        state = target;
        informationRequestCount = requestCount;
        facts = new HashMap<>(nextFacts);
        updatedAt = now;
    }
}
