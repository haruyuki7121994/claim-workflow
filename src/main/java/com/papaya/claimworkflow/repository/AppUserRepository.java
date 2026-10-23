package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.entity.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read-only directory access; user provisioning is an administrative operation. */
public interface AppUserRepository extends Repository<AppUser, UUID> {
    Optional<AppUser> findByTenantIdAndSubjectAndEnabledTrue(String tenantId, String subject);
}
