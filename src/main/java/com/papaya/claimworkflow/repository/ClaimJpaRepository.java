package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.entity.Claim;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimJpaRepository extends JpaRepository<Claim, UUID> {
    Optional<Claim> findByClaimIdAndTenantId(UUID claimId, String tenantId);
}
