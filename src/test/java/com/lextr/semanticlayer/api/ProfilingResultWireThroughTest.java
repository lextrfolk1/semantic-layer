package com.lextr.semanticlayer.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        ProfilingResultWireThroughTest.ProfilingResultWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class ProfilingResultWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTemplate() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
    }

    @Test
    void honorsProfilingMetricsContractEndToEnd() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow(objectId)),
                List.of(attributeRow()),
                List.of(profilingRow())
        ));

        mockMvc.perform(get("/api/profiling/metrics")
                        .queryParam("client_id", "client-a")
                        .queryParam("object_id", objectId.toString())
                        .queryParam("profiling_status_cd", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].client_id").value("client-a"))
                .andExpect(jsonPath("$[0].schema_cd").value("meta"))
                .andExpect(jsonPath("$[0].object_cd").value("GL_BALANCE"))
                .andExpect(jsonPath("$[0].attribute_name").value("Ledger Identifier Override"))
                .andExpect(jsonPath("$[0].inferred_role").value("SOURCE"))
                .andExpect(jsonPath("$[0].null_percentage").value(0))
                .andExpect(jsonPath("$[0].distinct_percentage").value(100))
                .andExpect(jsonPath("$[0].profiling_status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].last_profiled_at").value("2026-06-18T10:15:30Z"));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("meta.attribute_logical_name_override"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.profiling_result"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals(objectId, jdbcTemplate.recordedParameters().get(0).get("object_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals(objectId, jdbcTemplate.recordedParameters().get(1).get("object_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(2).get("client_id"));
        assertEquals("meta", jdbcTemplate.recordedParameters().get(2).get("schema_cd"));
        assertEquals("GL_BALANCE", jdbcTemplate.recordedParameters().get(2).get("object_cd"));
        assertEquals("COMPLETED", jdbcTemplate.recordedParameters().get(2).get("profiling_status_cd"));
    }

    @Test
    void returns404EndToEndWhenObjectUnknownWithinClientScope() throws Exception {
        jdbcTemplate.setResponses(List.of(List.of()));

        mockMvc.perform(get("/api/profiling/metrics")
                        .queryParam("client_id", "client-a")
                        .queryParam("object_id", "00000000-0000-0000-0000-000000000999"))
                .andExpect(status().isNotFound());

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
    }

    private static Map<String, Object> objectRow(UUID objectId) {
        Map<String, Object> row = new HashMap<>();
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("object_cd", "GL_BALANCE");
        row.put("object_nm", "GL Balance");
        row.put("effective_object_nm", "GL Balance Override");
        row.put("object_type_cd", "TABLE");
        row.put("schema_cd", "meta");
        row.put("connection_id", UUID.fromString("00000000-0000-0000-0000-000000000201"));
        row.put("data_classification_cd", "CONFIDENTIAL");
        row.put("pii_flg", true);
        row.put("confidential_flg", true);
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> profilingRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("client_id", "client-a");
        row.put("schema_cd", "meta");
        row.put("object_cd", "GL_BALANCE");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("attribute_role_cd", "SOURCE");
        row.put("null_pct_nbr", 0);
        row.put("distinct_pct_nbr", 100);
        row.put("profiling_status_cd", "COMPLETED");
        row.put("last_profiled_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_by", "profiler");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T11:15:30Z"));
        row.put("updated_by", "profiler");
        return row;
    }

    private static Map<String, Object> attributeRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("attribute_id", UUID.fromString("00000000-0000-0000-0000-000000000301"));
        row.put("object_id", UUID.fromString("00000000-0000-0000-0000-000000000101"));
        row.put("client_id", "client-a");
        row.put("attribute_cd", "ledger_id");
        row.put("attribute_nm", "ledger_id");
        row.put("effective_attribute_nm", "Ledger Identifier Override");
        row.put("data_type_cd", "NUMBER");
        row.put("taxonomy_cd", "finance");
        row.put("taxonomy_source_cd", "core");
        row.put("taxonomy_jurisdiction_cd", "GLOBAL");
        row.put("data_classification_cd", "CONFIDENTIAL");
        row.put("pii_flg", true);
        row.put("confidential_flg", false);
        row.put("masking_policy_cd", null);
        row.put("mnpi_flg", false);
        row.put("csi_flg", false);
        row.put("ai_exposure_cd", "LOW");
        row.put("pk_flg", false);
        row.put("fk_flg", false);
        row.put("nullable_flg", false);
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    @TestConfiguration
    static class ProfilingResultWireThroughTestConfiguration {

        @Bean
        ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate();
        }
    }
}
