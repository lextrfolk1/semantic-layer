package com.lextr.semanticlayer.logging;

import com.lextr.semanticlayer.api.ApiExceptionHandler;
import com.lextr.semanticlayer.api.WorkflowTaskController;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LoggingConfigurationStartupTest {

    @Test
    void startsWithStandardizedLoggingConfigurationLoaded() {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run()) {
            assertNotNull(context.getBean(ControllerExecutionTimeAspect.class));
            assertNotNull(context.getBean(ApiExceptionHandler.class));
            assertEquals("INFO", context.getEnvironment().getProperty("logging.level.com.lextr.semanticlayer"));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            JdbcRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            Neo4jAutoConfiguration.class,
            Neo4jDataAutoConfiguration.class
    })
    @Import({
            ControllerExecutionTimeAspect.class,
            ApiExceptionHandler.class,
            WorkflowTaskController.class,
            SQLQueryLoaderUtil.class
    })
    static class TestApplication {

        @Bean
        WorkflowApprovalService workflowApprovalService() {
            return new WorkflowApprovalService() {
                @Override
                public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
                    return null;
                }
            };
        }
    }
}
