package com.papaya.claimworkflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.papaya.claimworkflow.service.OutboxWorker;
import com.papaya.claimworkflow.service.WorkflowService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class MockConditionProfileTests {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(MvcConfig.class, MockConditionController.class)
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(WorkflowService.class, () -> new WorkflowService(
                    null, null, null, null, null, null, null, null, Clock.systemUTC()))
            .withBean(OutboxWorker.class, () -> new OutboxWorker(null, null, Clock.systemUTC(), 5, 60, 5));

    @Test
    void registersAssessmentPostRouteForLocalAndTestProfiles() {
        for (String profile : new String[] {"local", "test"}) {
            runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile)).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(MockConditionController.class);
                var mapping = context.getBean(RequestMappingHandlerMapping.class);
                assertThat(mapping.getHandlerMethods().keySet().stream()
                        .flatMap(info -> info.getPatternValues().stream()))
                        .contains("/api/v1/mock/complete-assessment/{claimId}");
            });
        }
    }

    @Test
    void doesNotRegisterSimulationRoutesForDefaultProfile() {
        runner.run(context -> assertThat(context).doesNotHaveBean(MockConditionController.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfig { }
}
