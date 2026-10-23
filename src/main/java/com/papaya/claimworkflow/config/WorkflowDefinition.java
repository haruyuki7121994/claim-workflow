package com.papaya.claimworkflow.config;

import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.Role;
import com.papaya.claimworkflow.enums.TransitionType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workflow")
public record WorkflowDefinition(String id, String version, ClaimState initialState, Task initialTask,
                                 Set<ClaimState> states, Map<String, Policy> policies, List<Transition> transitions) {
    public record Task(String type, Role requiredRole) { }
    public record Policy(int max, String error) { }
    public record Transition(TransitionType id, ClaimState from, ClaimState to, List<Role> roles,
                             List<String> guards, List<String> policies, List<ActionType> actions,
                             List<String> clearFacts, Task nextTask) {
        public Transition {
            roles = roles == null ? List.of() : List.copyOf(roles);
            guards = guards == null ? List.of() : List.copyOf(guards);
            policies = policies == null ? List.of() : List.copyOf(policies);
            actions = actions == null ? List.of() : List.copyOf(actions);
            clearFacts = clearFacts == null ? List.of() : List.copyOf(clearFacts);
        }
    }
}
