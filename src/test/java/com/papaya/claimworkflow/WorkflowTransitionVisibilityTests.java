package com.papaya.claimworkflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.papaya.claimworkflow.config.WorkflowDefinition;
import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.Role;
import com.papaya.claimworkflow.enums.TransitionType;
import com.papaya.claimworkflow.service.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowTransitionVisibilityTests {
    @Test
    void showsOtherRolesWithoutAllowingThemToExecuteTransitions() {
        var rule = new WorkflowDefinition.Transition(TransitionType.VERIFY_DOCUMENTS,
                ClaimState.SUBMITTED, ClaimState.DOCUMENTS_VERIFIED, List.of(Role.DOCUMENT_CLERK),
                List.of(), List.of(), List.of(), List.of(), null);
        var definition = new WorkflowDefinition("claims", "1", ClaimState.SUBMITTED,
                new WorkflowDefinition.Task("VERIFY_DOCUMENTS", Role.DOCUMENT_CLERK),
                Set.of(ClaimState.SUBMITTED, ClaimState.DOCUMENTS_VERIFIED),
                Map.of("informationRequestLimit", new WorkflowDefinition.Policy(3, "Limit exceeded")), List.of(rule));
        var handlers = new ActionHandlerRegistry(List.of(new NotificationActionHandler(),
                new PaymentActionHandler(), new InternalActionHandler()));
        var engine = new WorkflowEngine(definition, new GuardRegistry(List.of()), handlers);
        var claim = new Claim(UUID.randomUUID(), "tenant-a", ClaimState.SUBMITTED, "1", Map.of(), Instant.now());
        var finance = new Actor("finance-1", "tenant-a", Set.of(Role.FINANCE));
        var clerk = new Actor("clerk-1", "tenant-a", Set.of(Role.DOCUMENT_CLERK));

        assertThat(engine.available(claim, finance)).isEmpty();
        var visible = engine.available(claim, finance, true);
        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst()).containsEntry("authorized", false).containsEntry("ready", false)
                .containsEntry("requiredRoles", List.of(Role.DOCUMENT_CLERK));
        assertThat(engine.available(claim, clerk, true).getFirst())
                .containsEntry("authorized", true).containsEntry("ready", true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.authorize(rule, claim, finance))
                .isInstanceOf(com.papaya.claimworkflow.api.WorkflowException.class);
    }
}
