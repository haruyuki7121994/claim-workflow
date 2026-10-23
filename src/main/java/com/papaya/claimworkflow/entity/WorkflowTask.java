package com.papaya.claimworkflow.entity;

import com.papaya.claimworkflow.enums.Role;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "workflow_tasks")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowTask {
    @Id @Column(name = "task_id") private UUID taskId;
    @Column(name = "claim_id", nullable = false) private UUID claimId;
    @Column(name = "required_role", nullable = false) private Role requiredRole;
    @Column(nullable = false) private String status;
    @Column(name = "task_type", nullable = false) private String taskType;
    @Column(name = "assigned_to") private String assignedTo;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    public WorkflowTask(UUID taskId, UUID claimId, Role requiredRole, String taskType, String assignedTo, Instant now) {
        this.taskId = taskId; this.claimId = claimId; this.requiredRole = requiredRole; this.taskType = taskType;
        this.assignedTo = assignedTo; this.status = "PENDING"; this.createdAt = now;
    }

    public void complete(Instant now) { status = "COMPLETED"; completedAt = now; }
}
