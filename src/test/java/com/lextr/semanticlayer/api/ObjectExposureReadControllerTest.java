package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import com.lextr.semanticlayer.service.impl.ObjectExposureReadServiceImpl;
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

class ObjectExposureReadControllerTest {

    @Test
    void appliesObjectFiltersAndClientScoping() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao dao = new RecordingObjectExposureReadDao();
        dao.objectsByClient = Map.of(
                "client-a", List.of(new ObjectExposureRecord(
                        objectId,
                        "client-a",
                        "GL_BALANCE",
                        "GL Balance",
                        "GL Balance Override",
                        "TABLE",
                        "meta",
                        UUID.fromString("00000000-0000-0000-0000-000000000201"),
                        "ACTIVE",
                        OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                        "producer",
                        OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                        "platform"
                ))
        );
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/objects")
                        .queryParam("client_id", "client-a")
                        .queryParam("schema_cd", "meta")
                        .queryParam("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].object_id").value(objectId.toString()))
                .andExpect(jsonPath("$[0].object_nm").value("GL Balance Override"))
                .andExpect(jsonPath("$[0].lifecycle_status_cd").value("ACTIVE"));

        assertEquals("client-a", dao.lastClientId);
        assertEquals("meta", dao.lastSchemaCode);
        assertEquals("ACTIVE", dao.lastLifecycleStatusCode);
    }

    @Test
    void returnsObjectDetailWithinClientScope() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao dao = new RecordingObjectExposureReadDao();
        dao.objectsByClient = Map.of(
                "client-a", List.of(new ObjectExposureRecord(
                        objectId,
                        "client-a",
                        "GL_BALANCE",
                        "GL Balance",
                        "GL Balance Override",
                        "TABLE",
                        "meta",
                        UUID.fromString("00000000-0000-0000-0000-000000000201"),
                        "ACTIVE",
                        OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                        "producer",
                        null,
                        null
                ))
        );
        dao.attributesByObjectId = Map.of(
                objectId, List.of(new AttributeExposureRecord(
                        UUID.fromString("00000000-0000-0000-0000-000000000102"),
                        objectId,
                        "client-a",
                        "AMOUNT",
                        "Amount",
                        "Amount Override",
                        "DECIMAL",
                        "MDRM12345678",
                        "MDRM",
                        "US",
                        OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                        "producer",
                        null,
                        null
                ))
        );
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object_id").value(objectId.toString()))
                .andExpect(jsonPath("$.object_nm").value("GL Balance Override"))
                .andExpect(jsonPath("$.attributes[0].attribute_cd").value("AMOUNT"))
                .andExpect(jsonPath("$.attributes[0].attribute_nm").value("Amount Override"));

        assertEquals("client-a", dao.lastObjectClientId);
        assertEquals(objectId, dao.lastObjectId);
    }

    @Test
    void returns404ForUnknownObjectIdWithinClientScope() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingObjectExposureReadDao());

        mockMvc.perform(get("/api/objects/{object_id}", UUID.fromString("00000000-0000-0000-0000-000000000999"))
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(ObjectExposureReadDao dao) {
        ObjectExposureReadService readService = new ObjectExposureReadServiceImpl(dao);
        ObjectRegistrationController controller = new ObjectRegistrationController(new NoOpObjectRegistrationService(), readService);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private Map<String, List<ObjectExposureRecord>> objectsByClient = Map.of();
        private Map<UUID, List<AttributeExposureRecord>> attributesByObjectId = Map.of();
        private String lastClientId;
        private String lastSchemaCode;
        private String lastLifecycleStatusCode;
        private String lastObjectClientId;
        private UUID lastObjectId;

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            lastClientId = clientId;
            lastSchemaCode = schemaCode;
            lastLifecycleStatusCode = lifecycleStatusCode;
            return objectsByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> schemaCode == null || schemaCode.equals(record.schema_cd()))
                    .filter(record -> lifecycleStatusCode == null || lifecycleStatusCode.equals(record.lifecycle_status_cd()))
                    .toList();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            lastObjectClientId = clientId;
            lastObjectId = objectId;
            return objectsByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> objectId.equals(record.object_id()))
                    .findFirst();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return objectsByClient.values().stream()
                    .flatMap(List::stream)
                    .filter(record -> schemaCode.equals(record.schema_cd()))
                    .filter(record -> objectCode.equals(record.object_cd()))
                    .findFirst();
        }

        @Override
        public List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            return attributesByObjectId.getOrDefault(objectId, List.of()).stream()
                    .filter(record -> clientId.equals(record.client_id()))
                    .toList();
        }
    }

    private static final class NoOpObjectRegistrationService implements ObjectRegistrationService {

        @Override
        public ObjectRegistrationResponseDto registerObject(ObjectRegistrationRequestDto request) {
            throw new UnsupportedOperationException("Not used in read tests");
        }
    }
}
