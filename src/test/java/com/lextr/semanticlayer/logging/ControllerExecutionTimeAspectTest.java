package com.lextr.semanticlayer.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.api.ApiExceptionHandler;
import com.lextr.semanticlayer.api.WorkflowTaskController;
import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.dao.impl.JdbcRegistryReadDao;
import com.lextr.semanticlayer.dto.SchemaCatalogDto;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.WorkflowTaskAlreadyApprovedException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import com.lextr.semanticlayer.service.impl.RegistryReadServiceImpl;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class ControllerExecutionTimeAspectTest {

    @Test
    void logsSharedControllerTimingPathForSuccessfulRequests(CapturedOutput output) throws Exception {
        RecordingWorkflowApprovalService service = new RecordingWorkflowApprovalService();
        service.response = new WorkflowTaskResponseDto(
                301L,
                "FILTER_LOOKUP_REGISTRATION",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "APPROVED",
                "producer",
                null,
                null,
                null,
                "Review filter lookup LEDGER_SCOPE",
                "client-a",
                "approver",
                null,
                "looks good"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_status_cd").value("APPROVED"));

        assertEquals(301L, service.lastId);
        assertEquals("approver", service.lastRequest.approved_by());
        assertContains(output, "Handled request POST /api/workflow-tasks/301/approve via WorkflowTaskController.approveTask in ");
        assertFalse(output.getOut().contains("failed with status="));
    }

    @Test
    void logsHandledErrorsOnceWithoutDuplicateControllerFailureLogs(CapturedOutput output) throws Exception {
        RecordingWorkflowApprovalService service = new RecordingWorkflowApprovalService();
        service.error = new WorkflowTaskAlreadyApprovedException(301L);
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.message").value("Workflow task 301 is already approved and cannot be re-approved"));

        String failureLog = "Request POST /api/workflow-tasks/301/approve failed with status=422 code=UNPROCESSABLE_ENTITY exception=WorkflowTaskAlreadyApprovedException message=Workflow task 301 is already approved and cannot be re-approved";
        assertContains(output, failureLog);
        assertEquals(1, StringUtils.countOccurrencesOf(output.getOut(), failureLog));
        assertFalse(output.getOut().contains(
                "Handled request POST /api/workflow-tasks/301/approve via WorkflowTaskController.approveTask in "
        ));
    }

    @Test
    void logsSharedServiceTimingPathForSuccessfulInvocations(CapturedOutput output) {
        RecordingRegistryReadDao registryReadDao = new RecordingRegistryReadDao();
        registryReadDao.schemas = List.of(new SchemaCatalogRecord(
                "meta",
                "Metadata",
                "Metadata",
                "Semantic system of record",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "flyway",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "platform"
        ));
        RegistryReadServiceImpl service = proxied(new RegistryReadServiceImpl(registryReadDao));

        List<SchemaCatalogDto> schemas = service.findSchemas("client-a", "ACTIVE");

        assertEquals(1, schemas.size());
        assertContains(output, "Completed service RegistryReadServiceImpl.findSchemas in ");
    }

    @Test
    void logsSharedRepositoryTimingPathForSuccessfulInvocations(CapturedOutput output) {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(Map.of(
                "schema_cd", "meta",
                "schema_nm", "Metadata",
                "effective_schema_nm", "Metadata",
                "schema_purpose_txt", "Semantic system of record",
                "lifecycle_status_cd", "ACTIVE",
                "created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "created_by", "flyway",
                "updated_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "updated_by", "platform"
        )));
        JdbcRegistryReadDao dao = proxied(new JdbcRegistryReadDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        ));

        List<SchemaCatalogRecord> schemas = dao.findSchemas("client-a", "ACTIVE");

        assertEquals(1, schemas.size());
        assertContains(output, "Completed repository JdbcRegistryReadDao.findSchemas in ");
    }

    @Test
    void logsSharedComponentTimingPathAndStartupLoadForUtilityInvocations(CapturedOutput output) {
        SQLQueryLoaderUtil loader = proxied(new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        String query = loader.getQuery("schema_registry.find_all");

        org.junit.jupiter.api.Assertions.assertTrue(query.contains("schema_cd"));
        assertContains(output, "Loaded ");
        assertContains(output, "Completed component SQLQueryLoaderUtil.getQuery in ");
    }

    @Test
    void logsSharedServiceFailurePathForExceptions(CapturedOutput output) {
        RecordingRegistryReadDao registryReadDao = new RecordingRegistryReadDao();
        registryReadDao.error = new RuntimeException("registry offline");
        RegistryReadServiceImpl service = proxied(new RegistryReadServiceImpl(registryReadDao));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.findSchemas("client-a", "ACTIVE"));

        assertEquals("registry offline", exception.getMessage());
        assertContains(output, "Failed service RegistryReadServiceImpl.findSchemas in ");
        assertContains(output, "exception=RuntimeException message=registry offline");
    }

    private static MockMvc mockMvc(WorkflowApprovalService service) {
        WorkflowTaskController controller = new WorkflowTaskController(service, providerOf(null), null);
        WorkflowTaskController proxiedController = proxied(controller);

        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        return MockMvcBuilders.standaloneSetup(new Object[]{proxiedController})
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
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

    private static void assertContains(CapturedOutput output, String expected) {
        String combined = output.getOut() + output.getErr();
        org.junit.jupiter.api.Assertions.assertTrue(
                combined.contains(expected),
                () -> "Expected log output to contain: " + expected + "\nActual output:\n" + combined
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxied(T target) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new ControllerExecutionTimeAspect());
        return (T) proxyFactory.getProxy();
    }

    private static ObjectProvider<NamedParameterJdbcTemplate> providerOf(NamedParameterJdbcTemplate jdbcTemplate) {
        return new ObjectProvider<>() {
            @Override
            public NamedParameterJdbcTemplate getObject(Object... args) {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getIfAvailable() {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getIfUnique() {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getObject() {
                return jdbcTemplate;
            }

            @Override
            public Iterator<NamedParameterJdbcTemplate> iterator() {
                return jdbcTemplate == null ? Collections.emptyIterator() : List.of(jdbcTemplate).iterator();
            }
        };
    }

    private static final class RecordingWorkflowApprovalService implements WorkflowApprovalService {
        private Long lastId;
        private WorkflowApprovalRequestDto lastRequest;
        private WorkflowTaskResponseDto response;
        private RuntimeException error;

        @Override
        public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
            lastId = id;
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }

    private static final class RecordingRegistryReadDao implements RegistryReadDao {
        private List<SchemaCatalogRecord> schemas = List.of();
        private RuntimeException error;

        @Override
        public List<SchemaCatalogRecord> findSchemas(String clientId, String lifecycleStatusCode) {
            if (error != null) {
                throw error;
            }
            return schemas;
        }

        @Override
        public Optional<SchemaCatalogRecord> findSchema(String clientId, String schemaCode) {
            return schemas.stream().findFirst();
        }

        @Override
        public List<DataConnectionRecord> findConnections(String clientId, String engineCode, Boolean activeFlag) {
            return List.of();
        }

        @Override
        public Optional<DataConnectionRecord> findConnection(String clientId, UUID connectionId) {
            return Optional.empty();
        }
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final List<Map<String, Object>> rows;

        private RecordingNamedParameterJdbcTemplate(List<Map<String, Object>> rows) {
            super(noOpDataSource());
            this.rows = rows;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }

        private <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row) {
            try {
                return rowMapper.mapRow(resultSet(row), 0);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }

        private ResultSet resultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getString" -> (String) row.get(args[0]);
                        case "getBoolean" -> {
                            Object value = row.get(args[0]);
                            yield value != null && (Boolean) value;
                        }
                        case "getObject" -> {
                            if (args.length == 1) {
                                yield row.get(args[0]);
                            }
                            Object value = row.get(args[0]);
                            Class<?> requestedType = (Class<?>) args[1];
                            if (value == null || requestedType.isInstance(value)) {
                                yield value;
                            }
                            yield value;
                        }
                        case "wasNull" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    private static DataSource noOpDataSource() {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public Connection getConnection(String username, String password) {
                throw new UnsupportedOperationException("not needed");
            }
        };
    }
}
