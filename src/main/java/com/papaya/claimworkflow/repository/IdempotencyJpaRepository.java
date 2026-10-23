package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.entity.IdempotencyId;
import com.papaya.claimworkflow.entity.IdempotencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyJpaRepository extends JpaRepository<IdempotencyRequest, IdempotencyId> { }
