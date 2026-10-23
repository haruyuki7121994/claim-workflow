package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.api.WorkflowException;
import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.entity.Claim;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClaimRepository {
    private final ClaimJpaRepository repository;
    private final EntityManager entityManager;

    public Claim get(UUID id, Actor actor) {
        return repository.findByClaimIdAndTenantId(id, actor.tenantId())
                .orElseThrow(() -> new WorkflowException(404, "CLAIM_NOT_FOUND", "Claim not found"));
    }

    public List<Claim> list(Actor actor, int limit, int offset) {
        return entityManager.createQuery("""
                select c from Claim c where c.tenantId=:tenant order by c.createdAt desc,c.claimId
                """, Claim.class).setParameter("tenant", actor.tenantId())
                .setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public void insert(Claim claim) {
        repository.save(claim);
    }

    public void flush() {
        repository.flush();
    }
}
