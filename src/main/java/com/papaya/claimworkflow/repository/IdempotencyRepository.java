package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.api.WorkflowException;
import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.entity.IdempotencyId;
import com.papaya.claimworkflow.entity.IdempotencyRequest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IdempotencyRepository {
    private final IdempotencyJpaRepository repository;
    private final EntityManager entityManager;

    public void lock(Actor actor, String key) {
        String scope = actor.tenantId() + "|" + actor.id() + "|" + key;
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(cast(?1 as text), 0))")
                .setParameter(1, scope).getSingleResult();
    }

    public Optional<Map<String, Object>> replay(Actor actor, String key, String hash) {
        var existing = repository.findById(new IdempotencyId(actor.tenantId(), actor.id(), key));
        if (existing.isEmpty()) { return Optional.empty(); }
        if (!hash.equals(existing.get().requestHash())) {
            throw new WorkflowException(409, "IDEMPOTENCY_CONFLICT", "Idempotency key was already used for a different request");
        }
        return Optional.of(existing.get().responseBody());
    }

    public void save(Actor actor, String key, UUID claimId, String hash, Map<String, Object> result, Instant now) {
        repository.save(new IdempotencyRequest(actor.tenantId(), actor.id(), key, claimId, hash, result, now));
    }
}
