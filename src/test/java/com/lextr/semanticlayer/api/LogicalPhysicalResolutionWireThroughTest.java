package com.lextr.semanticlayer.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        LogicalPhysicalResolutionWireThroughTest.LogicalPhysicalResolutionWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class LogicalPhysicalResolutionWireThroughTest {

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
    void resolvesLogicalAttributesEndToEnd() throws Exception {
        jdbcTemplate.setResponses(List.of(List.of(attributeRow())));

        mockMvc.perform(get("/api/logical-physical-resolutions/attributes")
                        .queryParam("client_id", "client-a")
                        .queryParam("schema_cd", "meta")
                        .queryParam("object_cd", "ledger")
                        .queryParam("logical_attribute_cd", "ledger_id")
                        .queryParam("logical_attribute_cd", "account_nm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logical_attribute_cd").value("ledger_id"))
                .andExpect(jsonPath("$[0].effective_logical_attribute_nm").value("Ledger Identifier Override"))
                .andExpect(jsonPath("$[0].physical_attribute_nm").value("ledger_id"))
                .andExpect(jsonPath("$[0].source_object_nm").value("ledger_source"))
                .andExpect(jsonPath("$[0].engine_cd").value("POSTGRES"))
                .andExpect(jsonPath("$[0].data_type_cd").value("NUMBER"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog o"));
        assertEquals(List.of("ledger_id", "account_nm"), jdbcTemplate.recordedParameters().get(0).get("logical_attribute_cds"));
    }

    @Test
    void resolvesOutboundGrainEndToEnd() throws Exception {
        jdbcTemplate.setResponses(List.of(List.of(outboundRow())));

        mockMvc.perform(get("/api/logical-physical-resolutions/outbounds/77")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outbound_id").value(77))
                .andExpect(jsonPath("$[0].outbound_cd").value("OB-77"))
                .andExpect(jsonPath("$[0].grain_level_nbr").value(1))
                .andExpect(jsonPath("$[0].logical_attribute_cd").value("ledger_id"))
                .andExpect(jsonPath("$[0].effective_logical_attribute_nm").value("Ledger Identifier Override"))
                .andExpect(jsonPath("$[0].physical_attribute_nm").value("ledger_id"))
                .andExpect(jsonPath("$[0].source_object_nm").value("ledger_source"))
                .andExpect(jsonPath("$[0].engine_cd").value("POSTGRES"))
                .andExpect(jsonPath("$[0].data_type_cd").value("NUMBER"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.consumption_outbound co"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals(77L, jdbcTemplate.recordedParameters().get(0).get("outbound_id"));
    }

    private static Map<String, Object> attributeRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("outbound_id", null);
        row.put("outbound_cd", null);
        row.put("grain_level_nbr", null);
        row.put("client_id", "client-a");
        row.put("schema_cd", "meta");
        row.put("object_cd", "ledger");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("effective_logical_attribute_nm", "Ledger Identifier Override");
        row.put("physical_attribute_nm", "ledger_id");
        row.put("source_object_nm", "ledger_source");
        row.put("engine_cd", "POSTGRES");
        row.put("data_type_cd", "NUMBER");
        return row;
    }

    private static Map<String, Object> outboundRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("outbound_id", 77L);
        row.put("outbound_cd", "OB-77");
        row.put("grain_level_nbr", 1);
        row.put("client_id", "client-a");
        row.put("schema_cd", "meta");
        row.put("object_cd", "ledger");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("effective_logical_attribute_nm", "Ledger Identifier Override");
        row.put("physical_attribute_nm", "ledger_id");
        row.put("source_object_nm", "ledger_source");
        row.put("engine_cd", "POSTGRES");
        row.put("data_type_cd", "NUMBER");
        return row;
    }

    @Configuration
    static class LogicalPhysicalResolutionWireThroughTestConfiguration {

        @Bean
        ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate();
        }
    }
}
