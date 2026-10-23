package com.papaya.claimworkflow.api;

import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.dto.Requests;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.service.WorkflowService;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ClaimController {
    private final WorkflowService workflow;
    public ClaimController(WorkflowService workflow) { this.workflow = workflow; }

    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public Claim create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Requests.CreateClaim request) {
        return workflow.create(Actor.from(jwt), request);
    }

    @GetMapping("/claims")
    public List<Claim> list(@AuthenticationPrincipal Jwt jwt,
                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                           @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return workflow.list(Actor.from(jwt), limit, offset);
    }

    @GetMapping("/claims/{id}")
    public Claim get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return workflow.get(id, Actor.from(jwt));
    }

    @PatchMapping("/claims/{id}/evidence")
    public Claim evidence(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Requests.Evidence request) {
        return workflow.evidence(id, Actor.from(jwt), request);
    }

    @GetMapping("/claims/{id}/transitions")
    public Map<String, Object> transitions(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "false") boolean includeUnauthorized) {
        return workflow.transitions(id, Actor.from(jwt), includeUnauthorized);
    }

    @PostMapping("/claims/{id}/transitions")
    public Map<String, Object> transition(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                          @RequestHeader("Idempotency-Key") String key,
                                          @Valid @RequestBody Requests.Transition request) {
        return workflow.transition(id, Actor.from(jwt), key, request);
    }

    @GetMapping("/claims/{id}/audit")
    public List<Map<String, Object>> audit(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                                          @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return workflow.audit(id, Actor.from(jwt), limit, offset);
    }

    @GetMapping("/claims/{id}/effects")
    public List<Map<String, Object>> effects(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                                            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return workflow.effects(id, Actor.from(jwt), limit, offset);
    }

    @GetMapping("/claims/{id}/workflow")
    public Map<String, Object> progress(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return workflow.progress(id, Actor.from(jwt));
    }

    @GetMapping("/workflow-tasks")
    public List<Map<String, Object>> tasks(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
                                          @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return workflow.tasks(Actor.from(jwt), limit, offset);
    }
}
