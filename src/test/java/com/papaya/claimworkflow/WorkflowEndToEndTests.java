package com.papaya.claimworkflow;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.papaya.claimworkflow.api.WorkflowException;
import com.papaya.claimworkflow.domain.Actor;
import com.papaya.claimworkflow.dto.Requests;
import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ClaimState;
import com.papaya.claimworkflow.enums.Role;
import com.papaya.claimworkflow.service.*;
import java.util.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowEndToEndTests {
    private static final String TENANT = "tenant-a";
    private static final Actor CLERK = actor("clerk-1", TENANT, Role.DOCUMENT_CLERK);
    private static final Actor LEAD = actor("lead-1", TENANT, Role.TEAM_LEAD);
    private static final Actor ASSESSOR = actor("assessor-1", TENANT, Role.ASSESSOR);
    private static final Actor OTHER_ASSESSOR = actor("assessor-2", TENANT, Role.ASSESSOR);
    private static final Actor FINANCE = actor("finance-1", TENANT, Role.FINANCE);
    private static final Actor SYSTEM = actor("system-1", TENANT, Role.SYSTEM);

    private UUID scenarioClaimId;
    private final List<Map<String, Object>> scenarioRequests = new ArrayList<>();
    private static final Set<String> EXPORTED_SCENARIOS = Set.of("completesHappyPath", "completesRejectionPath",
            "completesInformationRequestLoop", "rejectsInvalidTransitionWithoutAudit", "rejectsUnauthorizedRole");

    @AfterEach
    void exportScenarioEvidence(TestInfo testInfo) throws java.io.IOException {
        String directory = System.getProperty("workflow.evidence-dir");
        String method = testInfo.getTestMethod().orElseThrow().getName();
        if (directory == null || !EXPORTED_SCENARIOS.contains(method)) return;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("testMethod", method);
        evidence.put("capturedAt", java.time.Instant.now().toString());
        evidence.put("claimId", scenarioClaimId);
        evidence.put("requests", scenarioRequests);
        evidence.put("auditSource", "PostgreSQL claim_transition_audit through WorkflowService.audit");
        evidence.put("auditTrail", null);
        if (scenarioClaimId != null) {
            try {
                evidence.put("auditTrail", workflow.audit(scenarioClaimId, CLERK, 200, 0));
                evidence.put("workflow", workflow.progress(scenarioClaimId, CLERK));
                evidence.put("effects", workflow.effects(scenarioClaimId, CLERK, 200, 0));
            } catch (Exception exception) {
                evidence.put("auditExportError", exception.getClass().getName() + ": " + exception.getMessage());
            }
        } else {
            evidence.put("auditExportError", "No claim was created before this scenario stopped");
        }
        var path = java.nio.file.Path.of(directory);
        java.nio.file.Files.createDirectories(path);
        java.nio.file.Files.writeString(path.resolve(method + ".json"),
                json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }

    @Autowired MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();
    @Autowired WorkflowService workflow;
    @Autowired OutboxWorker outboxWorker;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void createsClaimInSubmittedState() {
        Claim claim = create();
        assertThat(claim.state()).isEqualTo(ClaimState.SUBMITTED);
        assertThat(workflow.progress(claim.claimId(), CLERK).get("pendingTasks").toString()).contains("VERIFY_DOCUMENTS");
    }

    @Test
    @DisplayName("Scenario 4: SUBMITTED -> APPROVED is rejected without mutations")
    void rejectsInvalidTransitionWithoutAudit() {
        Claim claim = create();
        assertThatThrownBy(() -> transition(claim, CLERK, ClaimState.APPROVED))
                .isInstanceOfSatisfying(WorkflowException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("INVALID_TRANSITION");
                    assertThat(exception.getMessage()).isEqualTo("Transition SUBMITTED -> APPROVED is not allowed");
                    assertThat(exception.details()).containsKey("allowedTransitions");
                });
        assertThat(reload(claim, CLERK).version()).isEqualTo(claim.version());
        assertThat(workflow.effects(claim.claimId(), CLERK, 200, 0)).isEmpty();
        assertThat(workflow.audit(claim.claimId(), CLERK, 50, 0)).isEmpty();
        assertThat(reload(claim, CLERK).state()).isEqualTo(ClaimState.SUBMITTED);
    }

    @Test
    @DisplayName("Scenario 5: finance cannot approve an assessment")
    void rejectsUnauthorizedRole() {
        Claim claim = underAssessment();
        long version = claim.version();
        var tasksBefore = workflow.progress(claim.claimId(), CLERK).get("pendingTasks");
        var effectsBefore = workflow.effects(claim.claimId(), CLERK, 200, 0);
        assertError("UNAUTHORIZED_TRANSITION", () -> transition(claim, FINANCE, ClaimState.APPROVED));
        assertThat(reload(claim, CLERK).version()).isEqualTo(version);
        assertThat(reload(claim, CLERK).state()).isEqualTo(ClaimState.UNDER_ASSESSMENT);
        assertThat(workflow.audit(claim.claimId(), CLERK, 50, 0)).hasSize(2);
        assertThat(workflow.progress(claim.claimId(), CLERK).get("pendingTasks")).isEqualTo(tasksBefore);
        assertThat(workflow.effects(claim.claimId(), CLERK, 200, 0)).isEqualTo(effectsBefore);
    }

    @Test
    void rejectsFailedPrecondition() {
        Claim claim = create();
        assertError("PRECONDITION_FAILED", () -> transition(claim, CLERK, ClaimState.DOCUMENTS_VERIFIED));
    }

    @Test
    @DisplayName("Scenario 1: happy path closes with five ordered audit entries")
    void completesHappyPath() {
        Claim claim = underAssessment();
        claim = approveAndPay(claim);
        assertHistory(claim, ClaimState.DOCUMENTS_VERIFIED, ClaimState.UNDER_ASSESSMENT,
                ClaimState.APPROVED, ClaimState.PAYMENT_INITIATED, ClaimState.CLOSED);
    }

    @Test
    @DisplayName("Scenario 2: rejection closes after acknowledgement")
    void completesRejectionPath() {
        Claim claim = underAssessment();
        claim = evidence(claim, ASSESSOR, Map.of("assessmentComplete", true, "rejectionReason", "Policy exclusion"));
        claim = transition(claim, ASSESSOR, ClaimState.REJECTED);
        claim = evidence(claim, SYSTEM, Map.of("memberAcknowledged", true));
        claim = transition(claim, SYSTEM, ClaimState.CLOSED);
        assertHistory(claim, ClaimState.DOCUMENTS_VERIFIED, ClaimState.UNDER_ASSESSMENT,
                ClaimState.REJECTED, ClaimState.CLOSED);
    }

    @Test
    @DisplayName("Scenario 3: information loop continues through approval and payment to CLOSED")
    void completesInformationRequestLoop() {
        Claim claim = underAssessment();
        claim = informationLoop(claim, 1);
        assertThat(claim.informationRequestCount()).isEqualTo(1);
        claim = approveAndPay(claim);
        assertHistory(claim, ClaimState.DOCUMENTS_VERIFIED, ClaimState.UNDER_ASSESSMENT,
                ClaimState.PENDING_INFO, ClaimState.DOCUMENTS_VERIFIED, ClaimState.UNDER_ASSESSMENT,
                ClaimState.APPROVED, ClaimState.PAYMENT_INITIATED, ClaimState.CLOSED);
    }

    @Test
    void permitsExactlyThreeInformationRequests() {
        Claim claim = underAssessment();
        for (int count = 1; count <= 3; count++) claim = informationLoop(claim, count);
        assertThat(claim.informationRequestCount()).isEqualTo(3);
    }

    @Test
    void rejectsFourthInformationRequestWithRequiredMessage() {
        Claim claim = underAssessment();
        for (int count = 1; count <= 3; count++) claim = informationLoop(claim, count);
        Claim ready = evidence(claim, ASSESSOR, Map.of("missingInfoDescription", "Fourth request"));
        var historyBefore = workflow.audit(ready.claimId(), ASSESSOR, 200, 0);
        var tasksBefore = workflow.progress(ready.claimId(), ASSESSOR).get("pendingTasks");
        var effectsBefore = workflow.effects(ready.claimId(), ASSESSOR, 200, 0);
        assertThatThrownBy(() -> transition(ready, ASSESSOR, ClaimState.PENDING_INFO))
                .isInstanceOf(WorkflowException.class)
                .hasMessage("Maximum information requests exceeded — escalate to team lead");
        assertThat(reload(ready, ASSESSOR).informationRequestCount()).isEqualTo(3);
        assertThat(reload(ready, ASSESSOR).state()).isEqualTo(ClaimState.UNDER_ASSESSMENT);
        assertThat(reload(ready, ASSESSOR).version()).isEqualTo(ready.version());
        assertThat(workflow.audit(ready.claimId(), ASSESSOR, 200, 0)).isEqualTo(historyBefore);
        assertThat(workflow.progress(ready.claimId(), ASSESSOR).get("pendingTasks")).isEqualTo(tasksBefore);
        assertThat(workflow.effects(ready.claimId(), ASSESSOR, 200, 0)).isEqualTo(effectsBefore);
    }

    @Test
    void replaysSameIdempotentRequest() {
        Claim claim = evidence(create(), CLERK, Map.of("documentsComplete", true));
        Requests.Transition request = new Requests.Transition(ClaimState.DOCUMENTS_VERIFIED, claim.version(), "Verified");
        String key = "same-key-" + UUID.randomUUID();
        Map<String, Object> first = workflow.transition(claim.claimId(), CLERK, key, request);
        Map<String, Object> replay = workflow.transition(claim.claimId(), CLERK, key, request);
        assertThat(replay).isEqualTo(first);
        assertThat(workflow.audit(claim.claimId(), CLERK, 50, 0)).hasSize(1);
    }

    @Test
    void rejectsReusedIdempotencyKeyWithDifferentPayload() {
        Claim claim = evidence(create(), CLERK, Map.of("documentsComplete", true));
        String key = "conflict-key-" + UUID.randomUUID();
        workflow.transition(claim.claimId(), CLERK, key,
                new Requests.Transition(ClaimState.DOCUMENTS_VERIFIED, claim.version(), "Verified"));
        assertError("IDEMPOTENCY_CONFLICT", () -> workflow.transition(claim.claimId(), CLERK, key,
                new Requests.Transition(ClaimState.DOCUMENTS_VERIFIED, claim.version(), "Different reason")));
    }

    @Test
    void rejectsStaleExpectedVersion() {
        Claim claim = create();
        evidence(claim, CLERK, Map.of("documentsComplete", true));
        assertError("CONCURRENT_MODIFICATION", () -> workflow.evidence(claim.claimId(), CLERK,
                new Requests.Evidence(claim.version(), Map.of("documentsComplete", true))));
    }

    @Test
    void hidesClaimsAcrossTenants() {
        Claim claim = create();
        Actor outsider = actor("clerk-x", "tenant-b", Role.DOCUMENT_CLERK);
        assertError("CLAIM_NOT_FOUND", () -> workflow.get(claim.claimId(), outsider));
    }

    @Test
    void onlyAssignedAssessorCanChangeAssessmentEvidence() {
        Claim claim = underAssessment();
        assertError("UNAUTHORIZED_EVIDENCE", () -> evidence(claim, OTHER_ASSESSOR, Map.of("assessmentComplete", true)));
    }

    @Test
    void availableTransitionsAreFilteredByRole() {
        Claim claim = underAssessment();
        assertThat(workflow.transitions(claim.claimId(), FINANCE).get("transitions").toString()).isEqualTo("[]");
        assertThat(workflow.transitions(claim.claimId(), ASSESSOR).get("transitions").toString())
                .contains("APPROVED", "REJECTED", "PENDING_INFO");
    }

    @Test
    void outboxCompletesEffectsAndExposesProviderReceipt() {
        Claim claim = underAssessment();
        claim = evidence(claim, ASSESSOR, Map.of("assessmentComplete", true));
        claim = transition(claim, ASSESSOR, ClaimState.APPROVED);
        drainOutbox(claim.claimId());
        assertThat(workflow.effects(claim.claimId(), ASSESSOR, 50, 0))
                .allSatisfy(effect -> assertThat(effect.get("status")).isEqualTo("COMPLETED"));
        assertThat(workflow.effects(claim.claimId(), ASSESSOR, 50, 0).toString()).contains("PAY-");
    }

    @Test
    void databaseDeniesAuditUpdateDeleteAndTruncate() {
        Claim claim = evidence(create(), CLERK, Map.of("documentsComplete", true));
        transition(claim, CLERK, ClaimState.DOCUMENTS_VERIFIED);
        assertAuditMutationDenied("update claim_transition_audit set reason='changed' where claim_id='" + claim.claimId() + "'");
        assertAuditMutationDenied("delete from claim_transition_audit where claim_id='" + claim.claimId() + "'");
        assertAuditMutationDenied("truncate table claim_transition_audit");
    }

    @Test
    void rejectsMissingAssignee() {
        Claim claim = transition(evidence(create(), CLERK, Map.of("documentsComplete", true)),
                CLERK, ClaimState.DOCUMENTS_VERIFIED);
        assertError("PRECONDITION_FAILED", () -> transition(claim, LEAD, ClaimState.UNDER_ASSESSMENT));
    }

    @Test
    void rejectsIncompleteAssessment() {
        Claim claim = underAssessment();
        assertError("PRECONDITION_FAILED", () -> transition(claim, ASSESSOR, ClaimState.APPROVED));
    }

    @Test
    void rejectsMissingRejectionReason() {
        Claim claim = evidence(underAssessment(), ASSESSOR, Map.of("assessmentComplete", true));
        assertError("PRECONDITION_FAILED", () -> transition(claim, ASSESSOR, ClaimState.REJECTED));
    }

    @Test
    void rejectsMissingInformationDescription() {
        Claim claim = underAssessment();
        assertError("PRECONDITION_FAILED", () -> transition(claim, ASSESSOR, ClaimState.PENDING_INFO));
    }

    @Test
    void rejectsPaymentBeforeOutboxRequestCompletes() {
        Claim claim = transition(evidence(underAssessment(), ASSESSOR, Map.of("assessmentComplete", true)),
                ASSESSOR, ClaimState.APPROVED);
        assertError("PRECONDITION_FAILED", () -> transition(claim, FINANCE, ClaimState.PAYMENT_INITIATED));
    }

    @Test
    void rejectsClosureBeforeAcknowledgementOrAppealExpiry() {
        Claim claim = transition(evidence(underAssessment(), ASSESSOR,
                Map.of("assessmentComplete", true, "rejectionReason", "Excluded")), ASSESSOR, ClaimState.REJECTED);
        assertError("PRECONDITION_FAILED", () -> transition(claim, SYSTEM, ClaimState.CLOSED));
    }

    @Test
    void mockUploadPreservesStateAndDoesNotCreateAudit() {
        Claim claim = create();
        Claim uploaded = evidence(claim, CLERK, Map.of("documentsComplete", true));
        assertThat(uploaded.flag("documentsComplete")).isTrue();
        assertThat(uploaded.state()).isEqualTo(ClaimState.SUBMITTED);
        assertThat(uploaded.version()).isGreaterThan(claim.version());
        assertThat(workflow.audit(claim.claimId(), CLERK, 50, 0)).isEmpty();
    }

    @Test
    void mockUploadRejectsStaleVersion() {
        Claim claim = create();
        evidence(claim, CLERK, Map.of("documentsComplete", true));
        assertError("CONCURRENT_MODIFICATION", () -> evidence(claim, CLERK, Map.of("documentsComplete", true)));
    }

    @Test
    void rejectsAmountAbovePolicyLimit() {
        var response = postApi("/api/v1/claims", CLERK, Map.of("amount", "1000.01", "policyLimit", "1000.00"));
        Claim claim = workflow.get(UUID.fromString(response.get("claimId").asText()), CLERK);
        claim = transition(evidence(claim, CLERK, Map.of("documentsComplete", true)), CLERK, ClaimState.DOCUMENTS_VERIFIED);
        claim = transition(evidence(claim, LEAD, Map.of("assignedAssessorId", ASSESSOR.id())), LEAD, ClaimState.UNDER_ASSESSMENT);
        Claim ready = evidence(claim, ASSESSOR, Map.of("assessmentComplete", true));
        assertError("PRECONDITION_FAILED", () -> transition(ready, ASSESSOR, ClaimState.APPROVED));
    }

    @Test
    void rejectsReturnFromPendingInfoBeforeNewDocuments() {
        Claim claim = transition(evidence(underAssessment(), ASSESSOR,
                Map.of("missingInfoDescription", "Medical report")), ASSESSOR, ClaimState.PENDING_INFO);
        assertError("PRECONDITION_FAILED", () -> transition(claim, CLERK, ClaimState.DOCUMENTS_VERIFIED));
    }

    @Test
    void rejectsClosureWithoutPaymentReference() {
        Claim claim = transition(evidence(underAssessment(), ASSESSOR, Map.of("assessmentComplete", true)),
                ASSESSOR, ClaimState.APPROVED);
        postApi("/api/v1/mock/create-payment-request/" + claim.claimId(), FINANCE, Map.of());
        Claim initiated = transition(claim, FINANCE, ClaimState.PAYMENT_INITIATED);
        postApi("/api/v1/mock/process-payment/" + claim.claimId(), FINANCE, Map.of());
        assertError("PRECONDITION_FAILED", () -> transition(initiated, FINANCE, ClaimState.CLOSED));
    }

    @Test
    void closesRejectionAfterSimulatedAppealExpiry() {
        Claim claim = transition(evidence(underAssessment(), ASSESSOR,
                Map.of("assessmentComplete", true, "rejectionReason", "Excluded")), ASSESSOR, ClaimState.REJECTED);
        postApi("/api/v1/mock/expire-appeal/" + claim.claimId(), SYSTEM, Map.of("expectedVersion", claim.version()));
        claim = transition(reload(claim, SYSTEM), SYSTEM, ClaimState.CLOSED);
        assertHistory(claim, ClaimState.DOCUMENTS_VERIFIED, ClaimState.UNDER_ASSESSMENT,
                ClaimState.REJECTED, ClaimState.CLOSED);
    }

    private Claim create() {
        var response = postApi("/api/v1/claims", CLERK, Map.of("amount", "500.00", "policyLimit", "1000.00"));
        scenarioClaimId = UUID.fromString(response.get("claimId").asText());
        return workflow.get(scenarioClaimId, CLERK);
    }

    private Claim underAssessment() {
        Claim claim = evidence(create(), CLERK, Map.of("documentsComplete", true));
        claim = transition(claim, CLERK, ClaimState.DOCUMENTS_VERIFIED);
        claim = evidence(claim, LEAD, Map.of("assignedAssessorId", ASSESSOR.id()));
        return transition(claim, LEAD, ClaimState.UNDER_ASSESSMENT);
    }

    private Claim informationLoop(Claim claim, int count) {
        claim = evidence(claim, ASSESSOR, Map.of("missingInfoDescription", "Request " + count));
        claim = transition(claim, ASSESSOR, ClaimState.PENDING_INFO);
        claim = evidence(claim, CLERK, Map.of("newInformationReceived", true));
        claim = transition(claim, CLERK, ClaimState.DOCUMENTS_VERIFIED);
        return transition(claim, LEAD, ClaimState.UNDER_ASSESSMENT);
    }

    private Claim evidence(Claim claim, Actor actor, Map<String, Object> facts) {
        Claim result = claim;
        for (var fact : facts.entrySet()) {
            String operation = switch (fact.getKey()) {
                case "documentsComplete" -> "upload-documents";
                case "assignedAssessorId" -> "assign-assessor";
                case "assessmentComplete" -> "complete-assessment";
                case "missingInfoDescription" -> "missing-information";
                case "rejectionReason" -> "rejection-reason";
                case "newInformationReceived" -> "receive-information";
                case "paymentReference" -> "confirm-payment";
                case "memberAcknowledged" -> "acknowledge-rejection";
                default -> throw new IllegalArgumentException(fact.getKey());
            };
            Map<String, Object> body = new HashMap<>();
            body.put("expectedVersion", result.version());
            if (fact.getKey().equals("assignedAssessorId")) body.put("assessorId", fact.getValue());
            if (fact.getKey().endsWith("Description") || fact.getKey().equals("rejectionReason")) {
                body.put("description", fact.getValue());
            }
            if (fact.getKey().equals("paymentReference")) body.put("paymentReference", fact.getValue());
            postApi("/api/v1/mock/" + operation + "/" + claim.claimId(), actor, body);
            result = reload(claim, actor);
        }
        return result;
    }

    private Claim transition(Claim claim, Actor actor, ClaimState target) {
        postApi("/api/v1/claims/" + claim.claimId() + "/transitions", actor,
                Map.of("toState", target.name(), "expectedVersion", claim.version(), "reason", "Test transition"));
        return reload(claim, actor);
    }

    private Claim reload(Claim claim, Actor actor) { return workflow.get(claim.claimId(), actor); }

    private void drainOutbox(UUID claimId) {
        for (int count = 0; count < 1000; count++) {
            if (!outboxWorker.processOne(claimId)) return;
        }
        fail("Outbox did not drain within 1000 events");
    }

    private Claim approveAndPay(Claim claim) {
        claim = evidence(claim, ASSESSOR, Map.of("assessmentComplete", true));
        claim = transition(claim, ASSESSOR, ClaimState.APPROVED);
        postApi("/api/v1/mock/create-payment-request/" + claim.claimId(), FINANCE, Map.of());
        claim = transition(claim, FINANCE, ClaimState.PAYMENT_INITIATED);
        postApi("/api/v1/mock/process-payment/" + claim.claimId(), FINANCE, Map.of());
        claim = evidence(claim, FINANCE, Map.of("paymentReference", "PAYMENT-123"));
        return transition(claim, FINANCE, ClaimState.CLOSED);
    }

    private void assertHistory(Claim claim, ClaimState... states) {
        assertThat(claim.state()).isEqualTo(ClaimState.CLOSED);
        var history = workflow.audit(claim.claimId(), CLERK, 200, 0);
        assertThat(history).extracting(entry -> entry.get("toState")).containsExactly((Object[]) states);
        ClaimState previous = ClaimState.SUBMITTED;
        for (int index = 0; index < history.size(); index++) {
            var entry = history.get(index);
            assertThat(entry.get("fromState")).isEqualTo(previous);
            assertThat(entry.get("sequenceNumber")).isEqualTo((long) index + 1);
            assertThat(entry.get("actorId")).isNotNull();
            assertThat(entry.get("actorRole")).isNotNull();
            assertThat(entry.get("occurredAt")).isNotNull();
            assertThat(entry.get("reason")).isEqualTo("Test transition");
            previous = states[index];
        }
        assertThat(workflow.progress(claim.claimId(), CLERK).get("pendingTasks").toString()).isEqualTo("[]");
    }

    private tools.jackson.databind.JsonNode postApi(String path, Actor actor, Map<String, Object> body) {
        try {
            var result = mvc.perform(post(path).with(jwt().jwt(token -> token.subject(actor.id())
                            .claim("tenant_id", actor.tenantId())
                            .claim("roles", actor.roles().stream().map(Role::value).toList())))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();
            Map<String, Object> requestEvidence = new LinkedHashMap<>();
            requestEvidence.put("method", "POST");
            requestEvidence.put("path", path);
            requestEvidence.put("actorId", actor.id());
            requestEvidence.put("roles", actor.roles());
            requestEvidence.put("requestBody", body);
            requestEvidence.put("httpStatus", result.getResponse().getStatus());
            requestEvidence.put("responseBody", json.readTree(result.getResponse().getContentAsString()));
            scenarioRequests.add(requestEvidence);
            if (result.getResolvedException() instanceof WorkflowException exception) {
                assertThat(result.getResponse().getStatus()).isEqualTo(exception.status());
                assertThat(json.readTree(result.getResponse().getContentAsString()).get("code").asText())
                        .isEqualTo(exception.code());
                throw exception;
            }
            assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                    .isEqualTo(path.equals("/api/v1/claims") ? 201 : 200);
            return json.readTree(result.getResponse().getContentAsString());
        } catch (WorkflowException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssertionError("API request failed: " + path, exception);
        }
    }

    private void assertAuditMutationDenied(String sql) {
        var entityManager = entityManagerFactory.createEntityManager();
        var transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            assertThatThrownBy(() -> entityManager.createNativeQuery(sql).executeUpdate())
                    .isInstanceOf(RuntimeException.class)
                    .satisfies(exception -> {
                        Throwable cause = exception;
                        while (cause.getCause() != null) cause = cause.getCause();
                        assertThat(cause).isInstanceOf(java.sql.SQLException.class);
                        assertThat(((java.sql.SQLException) cause).getSQLState()).isEqualTo("42501");
                    });
        } finally {
            if (transaction.isActive()) transaction.rollback();
            entityManager.close();
        }
    }

    private static Actor actor(String id, String tenant, Role... roles) {
        return new Actor(id, tenant, Set.of(roles));
    }

    private static void assertError(String code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(WorkflowException.class,
                exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
