package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.entity.WorkflowTask;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTaskJpaRepository extends JpaRepository<WorkflowTask, UUID> {
    List<WorkflowTask> findByClaimIdAndStatusOrderByCreatedAtAsc(UUID claimId, String status);
}
