package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.config.WorkflowDefinition.Task;
import com.papaya.claimworkflow.domain.*;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.entity.WorkflowTask;
import com.papaya.claimworkflow.enums.Role;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TaskRepository {
    private final WorkflowTaskJpaRepository repository;
    private final EntityManager entityManager;

    public void create(UUID claimId, Task task, String assignee, Instant now) {
        if (task == null) { return; }
        repository.save(new WorkflowTask(UUID.randomUUID(), claimId, task.requiredRole(), task.type(),
                Role.ASSESSOR == task.requiredRole() ? assignee : null, now));
    }

    public void complete(UUID claimId, Instant now) {
        repository.findByClaimIdAndStatusOrderByCreatedAtAsc(claimId, "PENDING").forEach(task -> task.complete(now));
    }

    public List<Map<String, Object>> pending(UUID id) {
        return repository.findByClaimIdAndStatusOrderByCreatedAtAsc(id, "PENDING").stream().map(this::view).toList();
    }

    public List<Map<String, Object>> visible(Actor actor, int limit, int offset) {
        return entityManager.createQuery("""
                select t from WorkflowTask t, Claim c where c.claimId=t.claimId and c.tenantId=:tenant
                and t.status='PENDING' and t.requiredRole in :roles and (t.assignedTo is null or t.assignedTo=:actor)
                order by t.createdAt,t.taskId
                """, WorkflowTask.class).setParameter("tenant", actor.tenantId()).setParameter("roles", actor.roles())
                .setParameter("actor", actor.id()).setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(this::view).toList();
    }

    private Map<String, Object> view(WorkflowTask task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.taskId()); result.put("claimId", task.claimId());
        result.put("requiredRole", task.requiredRole()); result.put("status", task.status());
        result.put("taskType", task.taskType()); result.put("assignedTo", task.assignedTo());
        result.put("createdAt", task.createdAt()); result.put("completedAt", task.completedAt());
        return result;
    }
}
