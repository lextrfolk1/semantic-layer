package com.lextr.semanticlayer.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.api.ApiExceptionHandler;
import com.lextr.semanticlayer.api.WorkflowTaskController;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.WorkflowTaskAlreadyApprovedException;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void logsGlobalExceptionHandlingThroughStandardizedPattern(CapturedOutput output) throws Exception {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext context = application.run()) {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                            new WorkflowTaskController(new WorkflowApprovalService() {
                                @Override
                                public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
                                    throw new WorkflowTaskAlreadyApprovedException(id);
                                }

                                @Override
                                public WorkflowTaskResponseDto rejectTask(Long id, java.util.Map<String, String> body) {
                                    return null;
                                }
                            }, providerOf(null), null))
                    .setControllerAdvice(new ApiExceptionHandler())
                    .setValidator(newValidator())
                    .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
                    .build();

            mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                            .contentType("application/json")
                            .content(validRequestJson()))
                    .andExpect(status().isUnprocessableEntity());

            assertTrue((output.getOut() + output.getErr()).contains(
                    "[semantic-layer] Request POST /api/workflow-tasks/301/approve failed with status=422 code=UNPROCESSABLE_ENTITY"
            ));
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

    private static LocalValidatorFactoryBean newValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    private static String validRequestJson() {
        return """
                {
                  "client_id": "client-a",
                  "approved_by": "approver",
                  "approval_note_txt": "looks good"
                }
                """;
    }

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }

            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public java.util.Iterator<T> iterator() {
                return instance == null ? java.util.Collections.emptyIterator() : java.util.List.of(instance).iterator();
            }
        };
    }
}
