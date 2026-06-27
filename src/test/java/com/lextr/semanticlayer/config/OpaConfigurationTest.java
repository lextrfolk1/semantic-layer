package com.lextr.semanticlayer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.service.opa.OpaDecisionGateway;
import com.lextr.semanticlayer.service.WorkflowPolicyClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpaConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(OpaConfiguration.class);

    @Test
    void loadsWorkflowPolicyClientWhenOPAIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.opa.enabled=true",
                        "app.opa.base-url=http://localhost:8181",
                        "app.opa.decision-path-prefix=/v1/data",
                        "app.opa.request-timeout=1s"
                )
                .run(context -> {
                    assertNotNull(context.getBean(OpaDecisionGateway.class));
                    assertNotNull(context.getBean(WorkflowPolicyClient.class));
                });
    }

    @Test
    void doesNotLoadOPABeansWhenDisabled() {
        contextRunner
                .withPropertyValues("app.opa.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("opaDecisionGateway"));
                    assertFalse(context.containsBean("workflowPolicyClient"));
                    assertNull(context.getBeanProvider(OpaDecisionGateway.class).getIfAvailable());
                    assertNull(context.getBeanProvider(WorkflowPolicyClient.class).getIfAvailable());
                });
    }
}
