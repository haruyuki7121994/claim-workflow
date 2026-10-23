package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.api.*;
import com.papaya.claimworkflow.config.WorkflowDefinition;
import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.dto.Requests;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.enums.Role;
import com.papaya.claimworkflow.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final ClaimRepository claims;
    private final AuditRepository audits;
    private final TaskRepository tasks;
    private final OutboxRepository outbox;
    private final IdempotencyRepository idempotency;
    private final WorkflowDefinition definition;
    private final WorkflowEngine engine;
    private final EvidencePolicy evidence;
    private final Clock clock;

    public void requireStaff(Actor actor) {
        if (actor.roles().isEmpty()) {
            throw new WorkflowException(403, "FORBIDDEN", "A workflow staff role is required");
        }
    }

    @Transactional
    public Claim create(Actor actor, Requests.CreateClaim request) {
        if (!actor.has(Role.DOCUMENT_CLERK) && !actor.has(Role.TEAM_LEAD)) {
            throw new WorkflowException(403, "FORBIDDEN", "Only document clerks and team leads may create claims");
        }
        var now = clock.instant();
        var claim = new Claim(UUID.randomUUID(), actor.tenantId(), definition.initialState(), definition.version(),
                Map.of("amount", request.amount().setScale(2), "policyLimit", request.policyLimit().setScale(2)), now);
        claims.insert(claim);
        tasks.create(claim.claimId(), definition.initialTask(), null, now);
        return claim;
    }

    @Transactional(readOnly = true)
    public Claim get(UUID id, Actor actor) { requireStaff(actor); return claims.get(id, actor); }
    @Transactional(readOnly = true)
    public List<Claim> list(Actor actor, int limit, int offset) { requireStaff(actor); return claims.list(actor, limit, offset); }

    @Transactional
    public Claim evidence(UUID id, Actor actor, Requests.Evidence request) {
        Claim current = get(id, actor);
        if (current.version() != request.expectedVersion()) { throw WorkflowException.conflict(); }
        evidence.validate(current, actor, request.facts());
        current.applyEvidence(request.facts(), clock.instant());
        claims.flush();
        return current;
    }

    @Transactional
    public Map<String, Object> transition(UUID id, Actor actor, String key, Requests.Transition request) {
        requireStaff(actor);
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new WorkflowException(400, "INVALID_REQUEST", "Idempotency-Key must contain 1-128 letters, digits, dots, underscores, colons or hyphens");
        }
        String hash = hash(String.join("|", id.toString(), request.toState().name(), request.expectedVersion().toString(),
                request.reason(), actor.roles().stream().sorted().map(Role::value)
                        .collect(java.util.stream.Collectors.joining(","))));
        idempotency.lock(actor, key);
        Claim current = get(id, actor);
        var replay = idempotency.replay(actor, key, hash);
        if (replay.isPresent()) { return replay.get(); }
        if (current.version() != request.expectedVersion()) { throw WorkflowException.conflict(); }
        var rule = engine.resolve(current, request.toState());
        Role role = engine.authorize(rule, current, actor);
        engine.validate(rule, current);
        var now = clock.instant();
        var fromState = current.state();
        Map<String, Object> facts = new HashMap<>(current.facts());
        rule.clearFacts().forEach(facts::remove);
        if (rule.actions().contains(ActionType.NOTIFY_REJECTION)) {
            facts.put("appealDeadline", now.plus(30, ChronoUnit.DAYS).toString());
        }
        int count = current.informationRequestCount() + (rule.policies().contains("informationRequestLimit") ? 1 : 0);
        UUID transitionId = UUID.randomUUID();
        audits.append(transitionId, current, rule.to(), actor, role, request.reason(), now);
        current.transitionTo(rule.to(), count, facts, now);
        claims.flush();
        tasks.complete(id, now);
        tasks.create(id, rule.nextTask(), current.text("assignedAssessorId"), now);
        for (ActionType action : rule.actions()) { outbox.enqueue(current, transitionId, action, now); }
        Map<String, Object> response = Map.of("transitionId", transitionId.toString(), "claimId", id.toString(),
                "fromState", fromState.name(), "toState", rule.to().name(), "version", current.version(),
                "status", "COMMITTED", "sideEffects", Map.of("status", rule.actions().isEmpty() ? "COMPLETED" : "PENDING", "actions", rule.actions().stream().map(ActionType::value).toList()));
        idempotency.save(actor, key, id, hash, response, now);
        return response;
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> transitions(UUID id, Actor actor) {
        return transitions(id, actor, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> transitions(UUID id, Actor actor, boolean includeUnauthorized) {
        Claim claim = get(id, actor);
        return Map.of("claimId", id, "currentState", claim.state(), "transitions",
                engine.available(claim, actor, includeUnauthorized));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> audit(UUID id, Actor actor, int limit, int offset) {
        get(id, actor); return audits.list(id, limit, offset);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> effects(UUID id, Actor actor, int limit, int offset) {
        get(id, actor); return outbox.list(id, limit, offset);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tasks(Actor actor, int limit, int offset) {
        requireStaff(actor); return tasks.visible(actor, limit, offset);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> progress(UUID id, Actor actor) {
        Claim claim = get(id, actor);
        return Map.of("claimId", id, "workflow", definition.id(), "workflowVersion", claim.workflowVersion(),
                "version", claim.version(), "currentState", claim.state(), "pendingTasks", tasks.pending(id),
                "cycleCounters", Map.of("informationRequestCount", claim.informationRequestCount()),
                "pendingSideEffects", outbox.pendingCount(id), "historyCount", audits.count(id));
    }
}
