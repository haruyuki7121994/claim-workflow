package com.papaya.claimworkflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.papaya.claimworkflow.config.ApplicationConfig;
import com.papaya.claimworkflow.config.WorkflowDefinition;
import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.enums.Role;
import com.papaya.claimworkflow.enums.TransitionType;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class WorkflowDefinitionBindingTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("workflow", new ClassPathResource("workflow.yaml"))
                            .forEach(context.getEnvironment().getPropertySources()::addLast);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            })
            .withUserConfiguration(ApplicationConfig.class);

    @Test
    void bindsReadableYamlValuesToWorkflowEnums() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            WorkflowDefinition definition = context.getBean(WorkflowDefinition.class);
            assertThat(definition.initialTask().requiredRole()).isEqualTo(Role.DOCUMENT_CLERK);
            assertThat(definition.transitions().getFirst().id()).isEqualTo(TransitionType.VERIFY_DOCUMENTS);
            assertThat(definition.transitions().getFirst().actions()).containsExactly(ActionType.NOTIFY_ASSESSOR_TEAM);
        });
    }
}
