package com.lextr.semanticlayer.logging;

import com.lextr.semanticlayer.api.ApiExceptionHandler;
import com.lextr.semanticlayer.api.WorkflowTaskController;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class LoggingConfigurationStartupTest {

    @Test
    void startsWithStandardizedLoggingConfigurationLoaded(CapturedOutput output) {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run()) {
            assertNotNull(context.getBean(ControllerExecutionTimeAspect.class));
            assertNotNull(context.getBean(ApiExceptionHandler.class));
            assertEquals("INFO", context.getEnvironment().getProperty("logging.level.com.lextr.semanticlayer"));
            Logger logger = LoggerFactory.getLogger(LoggingConfigurationStartupTest.class);
            logger.info("logging config probe");
            assertTrue(output.getOut().contains("[semantic-layer] logging config probe"));
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
                @Override
                public WorkflowTaskResponseDto rejectTask(Long id, java.util.Map<String, String> body) {
                    return null;
                }
            };
        }
    }
}
