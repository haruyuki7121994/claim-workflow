package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.api.WorkflowException;
import com.papaya.claimworkflow.config.WorkflowDefinition;
import com.papaya.claimworkflow.config.WorkflowDefinition.Transition;
import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.Role;
import com.papaya.claimworkflow.enums.TransitionType;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WorkflowEngine {
    private final WorkflowDefinition definition;
    private final GuardRegistry guards;
    private final ActionHandlerRegistry handlers;

    public WorkflowEngine(WorkflowDefinition definition, GuardRegistry guards, ActionHandlerRegistry handlers) {
        this.definition = definition;
        this.guards = guards;
        this.handlers = handlers;
        validateDefinition();
    }

    private void validateDefinition() {
        if (definition.id() == null || definition.version() == null || definition.states() == null
                || !definition.states().contains(definition.initialState()) || definition.initialTask() == null
                || definition.initialTask().requiredRole() == null || definition.transitions() == null
                || definition.transitions().isEmpty() || definition.policies() == null) {
            throw new IllegalStateException("Incomplete workflow definition");
        }
        Set<TransitionType> ids = new HashSet<>();
        Set<String> edges = new HashSet<>();
        for (var rule : definition.transitions()) {
            boolean invalid = rule.id() == null || !ids.add(rule.id()) || !edges.add(rule.from() + "->" + rule.to())
                    || rule.from() == null || rule.to() == null || !definition.states().contains(rule.from())
                    || !definition.states().contains(rule.to()) || rule.roles() == null || rule.roles().isEmpty()
                    || rule.roles().stream().anyMatch(Objects::isNull) || rule.guards() == null || !guards.names().containsAll(rule.guards())
                    || rule.actions() == null || !handlers.supportsAll(rule.actions())
                    || new HashSet<>(rule.actions()).size() != rule.actions().size()
                    || rule.policies() == null || !Set.of("informationRequestLimit").containsAll(rule.policies())
                    || !definition.policies().keySet().containsAll(rule.policies())
                    || rule.clearFacts() == null || !Set.of("assessmentComplete", "newInformationReceived",
                            "missingInfoDescription", "rejectionReason").containsAll(rule.clearFacts())
                    || (rule.nextTask() != null && (rule.nextTask().requiredRole() == null || rule.nextTask().type() == null));
            if (invalid) {
                throw new IllegalStateException("Invalid workflow transition: " + rule.id());
            }
        }
        definition.policies().forEach((name, policy) -> {
            if (!"informationRequestLimit".equals(name) || policy.max() < 1 || policy.error() == null) {
                throw new IllegalStateException("Invalid workflow policy: " + name);
            }
        });
    }

    public Transition resolve(Claim claim, ClaimState target) {
        if (!definition.version().equals(claim.workflowVersion())) {
            throw new WorkflowException(409, "WORKFLOW_VERSION_MISMATCH", "Claim requires workflow version " + claim.workflowVersion());
        }
        return definition.transitions().stream().filter(rule -> rule.from().equals(claim.state()) && rule.to().equals(target))
                .findFirst().orElseThrow(() -> new WorkflowException(409, "INVALID_TRANSITION",
                        "Transition " + claim.state() + " -> " + target + " is not allowed",
                        Map.of("allowedTransitions", from(claim).stream().map(Transition::to).toList())));
    }

    public Role authorize(Transition rule, Claim claim, Actor actor) {
        Role role = rule.roles().stream().filter(actor::has).findFirst().orElseThrow(() ->
                new WorkflowException(403, "UNAUTHORIZED_TRANSITION", "Transition requires role: "
                        + rule.roles().stream().map(Role::value).collect(java.util.stream.Collectors.joining(", "))));
        if (Role.ASSESSOR == role && !actor.id().equals(claim.text("assignedAssessorId"))) {
            throw new WorkflowException(403, "UNAUTHORIZED_TRANSITION", "Only the assigned assessor may perform this transition");
        }
        return role;
    }

    public void validate(Transition rule, Claim claim) {
        for (String guard : rule.guards()) {
            if (!guards.passes(guard, claim)) {
                throw new WorkflowException(409, "PRECONDITION_FAILED", "Precondition failed: " + guard, Map.of("guard", guard));
            }
        }
        if (rule.policies().contains("informationRequestLimit") && claim.informationRequestCount() >= limit()) {
            throw new WorkflowException(409, "CYCLE_LIMIT_EXCEEDED", definition.policies().get("informationRequestLimit").error(),
                    Map.of("currentState", claim.state(), "attemptedState", rule.to(),
                            "currentCount", claim.informationRequestCount(), "maxAllowed", limit()));
        }
    }

    public List<Transition> from(Claim claim) {
        return definition.transitions().stream().filter(rule -> rule.from().equals(claim.state())).toList();
    }

    public List<Map<String, Object>> available(Claim claim, Actor actor) {
        return available(claim, actor, false);
    }

    public List<Map<String, Object>> available(Claim claim, Actor actor, boolean includeUnauthorized) {
        return from(claim).stream().filter(rule -> {
            if (includeUnauthorized) return true;
            try { authorize(rule, claim, actor); return true; } catch (WorkflowException exception) { return false; }
        }).map(rule -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("toState", rule.to());
            value.put("requiredRoles", rule.roles());
            value.put("preconditions", rule.guards());
            value.put("authorized", false);
            try {
                authorize(rule, claim, actor);
                value.put("authorized", true);
                validate(rule, claim);
                value.put("ready", true);
            } catch (WorkflowException exception) {
                value.put("ready", false);
                value.put("blockedBy", Map.of("code", exception.code(), "message", exception.getMessage()));
            }
            if (rule.policies().contains("informationRequestLimit")) {
                value.put("remainingInformationRequests", Math.max(0, limit() - claim.informationRequestCount()));
            }
            return value;
        }).toList();
    }

    public int limit() { return definition.policies().get("informationRequestLimit").max(); }
}
