package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dao.ObservabilitySignalDao;
import com.lextr.semanticlayer.dto.ObservabilitySignalCorrelationRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalIngestRequestDto;
import com.lextr.semanticlayer.service.ObservabilitySignalService;
import com.lextr.semanticlayer.service.impl.ObservabilitySignalServiceImpl;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        ObservabilitySignalWireThroughTest.ObservabilitySignalWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class ObservabilitySignalWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
    }

    @Test
    void honorsObservabilitySignalIngestContractEndToEnd() throws Exception {
        OffsetDateTime detectedAt = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        OffsetDateTime persistedAt = OffsetDateTime.parse("2026-06-18T10:16:30Z");
        jdbcTemplate.setResponses(List.of(List.of(signalRow(
                101L,
                "client-a",
                "FRESHNESS",
                "INFO",
                "OPEN",
                null,
                null,
                detectedAt,
                persistedAt,
                "tooling"
        ))));

        mockMvc.perform(post("/api/observability-signals")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "signal_type_cd": "FRESHNESS",
                                  "source_system_cd": "PIPELINE",
                                  "source_entity_type_cd": "DATASET",
                                  "source_entity_ref_txt": "orders",
                                  "correlation_key_txt": "orders#2026-06-18",
                                  "finding_summary_txt": "Freshness lag detected",
                                  "finding_detail_txt": "Latest event lagged by 4h",
                                  "dq_rerun_requested_flg": true,
                                  "dq_rerun_reason_txt": "Re-run ETL",
                                  "detected_ts": "2026-06-18T10:15:30Z",
                                  "reported_by": "tooling"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.client_id").value("client-a"))
                .andExpect(jsonPath("$.signal_type_cd").value("FRESHNESS"))
                .andExpect(jsonPath("$.severity_cd").value("INFO"))
                .andExpect(jsonPath("$.signal_status_cd").value("OPEN"))
                .andExpect(jsonPath("$.dq_rerun_requested_flg").value(true))
                .andExpect(jsonPath("$.created_by").value("tooling"))
                .andExpect(jsonPath("$.updated_by").value("tooling"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("INSERT INTO meta.observability_signal"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("FRESHNESS", jdbcTemplate.recordedParameters().get(0).get("signal_type_cd"));
        assertEquals("INFO", jdbcTemplate.recordedParameters().get(0).get("severity_cd"));
        assertEquals("OPEN", jdbcTemplate.recordedParameters().get(0).get("signal_status_cd"));
        assertEquals("tooling", jdbcTemplate.recordedParameters().get(0).get("created_by"));
        assertEquals("tooling", jdbcTemplate.recordedParameters().get(0).get("updated_by"));
    }

    @Test
    void honorsObservabilitySignalCorrelationContractEndToEnd() throws Exception {
        OffsetDateTime acknowledgedAt = OffsetDateTime.parse("2026-06-18T10:20:30Z");
        OffsetDateTime resolvedAt = OffsetDateTime.parse("2026-06-18T11:20:30Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-06-18T10:25:30Z");
        jdbcTemplate.setResponses(List.of(List.of(signalRow(
                101L,
                "client-a",
                "FRESHNESS",
                "WARN",
                "TRIAGE",
                701L,
                "Create DQ rerun",
                acknowledgedAt,
                resolvedAt,
                "analyst"
        ))));

        mockMvc.perform(post("/api/observability-signals/{signal_id}/correlate", 101L)
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "signal_status_cd": "TRIAGE",
                                  "workflow_task_id": 701,
                                  "dq_rerun_requested_flg": true,
                                  "dq_rerun_reason_txt": "Create DQ rerun",
                                  "acknowledged_ts": "2026-06-18T10:20:30Z",
                                  "resolved_ts": "2026-06-18T11:20:30Z",
                                  "correlated_by": "analyst"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.client_id").value("client-a"))
                .andExpect(jsonPath("$.signal_status_cd").value("TRIAGE"))
                .andExpect(jsonPath("$.workflow_task_id").value(701))
                .andExpect(jsonPath("$.dq_rerun_requested_flg").value(true))
                .andExpect(jsonPath("$.dq_rerun_reason_txt").value("Create DQ rerun"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("UPDATE meta.observability_signal"));
        assertEquals(101L, jdbcTemplate.recordedParameters().get(0).get("id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("TRIAGE", jdbcTemplate.recordedParameters().get(0).get("signal_status_cd"));
        assertEquals(701L, jdbcTemplate.recordedParameters().get(0).get("workflow_task_id"));
        assertEquals("analyst", jdbcTemplate.recordedParameters().get(0).get("updated_by"));
    }

    private static Map<String, Object> signalRow(Long id,
                                                 String clientId,
                                                 String signalTypeCode,
                                                 String severityCode,
                                                 String signalStatusCode,
                                                 Long workflowTaskId,
                                                 String dqRerunReasonText,
                                                 OffsetDateTime acknowledgedAt,
                                                 OffsetDateTime resolvedAt,
                                                 String updatedBy) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("client_id", clientId);
        row.put("signal_type_cd", signalTypeCode);
        row.put("severity_cd", severityCode);
        row.put("signal_status_cd", signalStatusCode);
        row.put("source_system_cd", "PIPELINE");
        row.put("source_entity_type_cd", "DATASET");
        row.put("source_entity_ref_txt", "orders");
        row.put("correlation_key_txt", "orders#2026-06-18");
        row.put("finding_summary_txt", "Freshness lag detected");
        row.put("finding_detail_txt", "Latest event lagged by 4h");
        row.put("detected_ts", timestamp);
        row.put("acknowledged_ts", acknowledgedAt);
        row.put("resolved_ts", resolvedAt);
        row.put("workflow_task_id", workflowTaskId);
        row.put("dq_rerun_requested_flg", true);
        row.put("dq_rerun_reason_txt", dqRerunReasonText);
        row.put("created_ts", timestamp);
        row.put("created_by", "tooling");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T10:16:30Z"));
        row.put("updated_by", updatedBy);
        return row;
    }

    @TestConfiguration
    static class ObservabilitySignalWireThroughTestConfiguration {

        @Bean
        ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        @Primary
        ObservabilitySignalService observabilitySignalService(ObjectProvider<ObservabilitySignalDao> observabilitySignalDaoProvider) {
            return new ObservabilitySignalServiceImpl(observabilitySignalDaoProvider);
        }

        @Bean
        @Primary
        ObservabilitySignalDao observabilitySignalDao(ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate jdbcTemplate) {
            return new com.lextr.semanticlayer.dao.impl.JdbcObservabilitySignalDao(
                    providerOf(jdbcTemplate),
                    new SQLQueryLoaderUtil(new DefaultResourceLoader())
            );
        }
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
            public Iterator<T> iterator() {
                return instance == null ? Collections.emptyIterator() : List.of(instance).iterator();
            }
        };
    }
}
