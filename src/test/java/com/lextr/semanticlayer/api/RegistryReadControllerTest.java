package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
import com.lextr.semanticlayer.service.RegistryReadService;
import com.lextr.semanticlayer.service.impl.RegistryReadServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistryReadControllerTest {

    @Test
    void appliesSchemaFiltersAndClientScoping() throws Exception {
        RecordingRegistryReadDao dao = new RecordingRegistryReadDao();
        dao.schemasByClient = Map.of(
                "client-a", List.of(new SchemaCatalogRecord(
                        "meta",
                        "Metadata",
                        "Metadata",
                        "Semantic system of record",
                        "ACTIVE",
                        OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                        "flyway",
                        OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                        "platform"
                ))
        );
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/registry/schemas")
                        .queryParam("client_id", "client-a")
                        .queryParam("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schema_cd").value("meta"))
                .andExpect(jsonPath("$[0].lifecycle_status_cd").value("ACTIVE"));

        assertEquals("client-a", dao.lastSchemaClientId);
        assertEquals("ACTIVE", dao.lastLifecycleStatusCode);
    }

    @Test
    void appliesConnectionFiltersAndClientScoping() throws Exception {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RecordingRegistryReadDao dao = new RecordingRegistryReadDao();
        dao.connectionsByClient = Map.of(
                "client-a", List.of(new DataConnectionRecord(
                        connectionId,
                        "LEXTR_PG",
                        "Lextr PostgreSQL",
                        "Lextr PostgreSQL",
                        "POSTGRES",
                        "PRIMARY",
                        "METADATA_PLUS_EXECUTION",
                        "localhost",
                        5432,
                        "lextr",
                        "meta",
                        true,
                        true,
                        OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                        "flyway",
                        null,
                        null
                ))
        );
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/registry/connections")
                        .queryParam("client_id", "client-a")
                        .queryParam("engine_cd", "POSTGRES")
                        .queryParam("is_active_flg", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connection_id").value(connectionId.toString()))
                .andExpect(jsonPath("$[0].engine_cd").value("POSTGRES"));

        assertEquals("client-a", dao.lastConnectionClientId);
        assertEquals("POSTGRES", dao.lastEngineCode);
        assertEquals(Boolean.TRUE, dao.lastActiveFlag);
    }

    @Test
    void returns404ForUnknownSchemaIdWithinClientScope() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingRegistryReadDao());

        mockMvc.perform(get("/api/registry/schemas/unknown")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsSchemaByCodeWithinClientScope() throws Exception {
        RecordingRegistryReadDao dao = new RecordingRegistryReadDao();
        dao.schemasByClient = Map.of(
                "client-a", List.of(new SchemaCatalogRecord(
                        "meta",
                        "Metadata",
                        "Metadata Override",
                        "Semantic system of record",
                        "ACTIVE",
                        OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                        "flyway",
                        OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                        "platform"
                ))
        );
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/registry/schemas/{schema_code}", "meta")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema_cd").value("meta"))
                .andExpect(jsonPath("$.schema_nm").value("Metadata Override"))
                .andExpect(jsonPath("$.lifecycle_status_cd").value("ACTIVE"));
    }

    @Test
    void returns404ForUnknownConnectionIdWithinClientScope() throws Exception {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        MockMvc mockMvc = mockMvc(new RecordingRegistryReadDao());

        mockMvc.perform(get("/api/registry/connections/{connection_id}", connectionId)
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(RegistryReadDao dao) {
        RegistryReadService service = new RegistryReadServiceImpl(dao);
        RegistryReadController controller = new RegistryReadController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingRegistryReadDao implements RegistryReadDao {

        private Map<String, List<SchemaCatalogRecord>> schemasByClient = Map.of();
        private Map<String, List<DataConnectionRecord>> connectionsByClient = Map.of();
        private String lastSchemaClientId;
        private String lastLifecycleStatusCode;
        private String lastConnectionClientId;
        private String lastEngineCode;
        private Boolean lastActiveFlag;

        @Override
        public List<SchemaCatalogRecord> findSchemas(String clientId, String lifecycleStatusCode) {
            lastSchemaClientId = clientId;
            lastLifecycleStatusCode = lifecycleStatusCode;
            return schemasByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> lifecycleStatusCode == null || lifecycleStatusCode.equals(record.lifecycle_status_cd()))
                    .toList();
        }

        @Override
        public Optional<SchemaCatalogRecord> findSchema(String clientId, String schemaCode) {
            return schemasByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> schemaCode.equals(record.schema_cd()))
                    .findFirst();
        }

        @Override
        public List<DataConnectionRecord> findConnections(String clientId, String engineCode, Boolean activeFlag) {
            lastConnectionClientId = clientId;
            lastEngineCode = engineCode;
            lastActiveFlag = activeFlag;
            return connectionsByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> engineCode == null || engineCode.equals(record.engine_cd()))
                    .filter(record -> activeFlag == null || activeFlag.equals(record.is_active_flg()))
                    .toList();
        }

        @Override
        public Optional<DataConnectionRecord> findConnection(String clientId, UUID connectionId) {
            return connectionsByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> connectionId.equals(record.connection_id()))
                    .findFirst();
        }
    }
}
