package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.entity.EffectReceipt;
import com.papaya.claimworkflow.entity.OutboxEvent;
import com.papaya.claimworkflow.enums.ActionType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class OutboxRepository {
    public record Event(UUID eventId, UUID claimId, ActionType action, Map<String, Object> payload, int attempts, UUID leaseToken) { }
    private final OutboxEventJpaRepository repository;
    private final EffectReceiptJpaRepository receipts;
    private final EntityManager entityManager;

    public void enqueue(Claim claim, UUID transitionId, ActionType action, Instant now) {
        repository.save(new OutboxEvent(UUID.randomUUID(), claim.claimId(), transitionId, action,
                Map.of("claimId", claim.claimId().toString(), "facts", claim.facts()), now));
    }

    public boolean completed(UUID id, ActionType action) {
        return repository.existsByClaimIdAndActionTypeAndStatus(id, action, "COMPLETED");
    }

    public long pendingCount(UUID id) {
        return repository.countByClaimIdAndStatusNot(id, "COMPLETED");
    }

    public List<Map<String, Object>> list(UUID id, int limit, int offset) {
        return entityManager.createQuery("""
                select e from OutboxEvent e where e.claimId=:id order by e.createdAt,e.eventId
                """, OutboxEvent.class).setParameter("id", id).setFirstResult(offset).setMaxResults(limit)
                .getResultList().stream().map(event -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("eventId", event.eventId()); result.put("action", event.actionType()); result.put("status", event.status());
            result.put("attempts", event.retryCount()); result.put("lastError", event.lastError());
            result.put("nextRetryAt", event.nextRetryAt());
            result.put("result", receipts.findById(event.eventId()).map(EffectReceipt::getResult).orElse(null));
            return result;
        }).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Event> claimNext(UUID claimId, Instant now, int leaseSeconds, int maxAttempts) {
        repository.expireFinalLeases(claimId, now, maxAttempts);
        var candidates = repository.findClaimable(claimId, now, maxAttempts, PageRequest.of(0, 1));
        if (candidates.isEmpty()) return Optional.empty();
        OutboxEvent entity = candidates.getFirst();
        UUID token = UUID.randomUUID();
        entity.claim(token, now.plusSeconds(leaseSeconds));
        return Optional.of(new Event(entity.eventId(), entity.claimId(), entity.actionType(), entity.payload(), entity.retryCount(), token));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(Event event, String result, Instant now) {
        OutboxEvent entity = repository.findById(event.eventId()).orElseThrow();
        if (!"PROCESSING".equals(entity.status()) || !event.leaseToken().equals(entity.leaseToken())) return;
        receipts.save(new EffectReceipt(event.eventId(), result, now));
        entity.succeed(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Event event, String message, Instant now, int maxAttempts, int retrySeconds) {
        long delay = Math.min(3600, retrySeconds * (1L << Math.min(event.attempts() - 1, 10)));
        OutboxEvent entity = repository.findById(event.eventId()).orElseThrow();
        if (!"PROCESSING".equals(entity.status()) || !event.leaseToken().equals(entity.leaseToken())) return;
        entity.fail(message.substring(0, Math.min(message.length(), 1000)), now.plusSeconds(delay), event.attempts() >= maxAttempts);
    }
}
