package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.entity.OutboxEvent;
import com.papaya.claimworkflow.enums.ActionType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, UUID> {
    boolean existsByClaimIdAndActionTypeAndStatus(UUID claimId, ActionType actionType, String status);
    long countByClaimIdAndStatusNot(UUID claimId, String status);
    List<OutboxEvent> findByClaimIdOrderByCreatedAtAscEventIdAsc(UUID claimId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from OutboxEvent e where (:claimId is null or e.claimId = :claimId) and e.retryCount < :maxAttempts and
            ((e.status in ('PENDING','FAILED_RETRYABLE') and e.nextRetryAt <= :now)
             or (e.status='PROCESSING' and e.leaseUntil <= :now))
            order by e.createdAt,e.eventId
            """)
    List<OutboxEvent> findClaimable(@Param("claimId") UUID claimId, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Modifying
    @Query("""
            update OutboxEvent e set e.status='FAILED',e.lastError='Worker lease expired after final attempt',
            e.leaseToken=null,e.leaseUntil=null where (:claimId is null or e.claimId=:claimId) and e.status='PROCESSING' and e.leaseUntil<=:now and e.retryCount>=:maxAttempts
            """)
    int expireFinalLeases(@Param("claimId") UUID claimId, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);
}
