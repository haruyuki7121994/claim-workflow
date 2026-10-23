package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.entity.ClaimAudit;
import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.Role;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Intentionally exposes only insert and read operations. */
@Repository
@RequiredArgsConstructor
public class AuditRepository {
    private final EntityManager entityManager;

    public void append(UUID id, Claim claim, ClaimState target, Actor actor, Role role, String reason, Instant now) {
        long sequence = entityManager.createQuery(
                "select coalesce(max(a.sequenceNumber), 0) + 1 from ClaimAudit a where a.claimId = :claimId", Long.class)
                .setParameter("claimId", claim.claimId()).getSingleResult();
        entityManager.persist(new ClaimAudit(id, claim.claimId(), claim.state(), target, actor.id(), role,
                sequence, reason, claim.workflowVersion(), now));
    }

    public List<Map<String, Object>> list(UUID id, int limit, int offset) {
        return entityManager.createQuery("select a from ClaimAudit a where a.claimId=:id order by a.sequenceNumber", ClaimAudit.class)
                .setParameter("id", id).setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(a -> Map.<String, Object>of("auditId", a.auditId(), "claimId", a.claimId(),
                        "fromState", a.fromState(), "toState", a.toState(), "actorId", a.actorId(),
                        "actorRole", a.actorRole(), "sequenceNumber", a.sequenceNumber(), "reason", a.reason(),
                        "workflowVersion", a.workflowVersion(), "occurredAt", a.occurredAt())).toList();
    }

    public long count(UUID id) {
        return entityManager.createQuery("select count(a) from ClaimAudit a where a.claimId=:id", Long.class)
                .setParameter("id", id).getSingleResult();
    }
}
