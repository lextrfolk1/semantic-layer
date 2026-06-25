package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.AttributeRegistrationResponseDto;
import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.exception.ObjectRegistrationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObjectRegistrationControllerTest {

    @Test
    void routesObjectRegistrationEndpointToService() throws Exception {
        RecordingObjectRegistrationService service = new RecordingObjectRegistrationService();
        service.response = new ObjectRegistrationResponseDto(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "GL_BALANCE",
                "GL Balance",
                "DRAFT",
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                "PENDING_APPROVAL",
                UUID.fromString("00000000-0000-0000-0000-000000000401"),
                List.of(new AttributeRegistrationResponseDto(
                        UUID.fromString("00000000-0000-0000-0000-000000000102"),
                        "AMOUNT",
                        "Amount",
                        "MDRM12345678",
                        "MDRM",
                        "US",
                        true,
                        false,
                        false
                ))
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/objects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "object_cd": "GL_BALANCE",
                                  "object_nm": "GL Balance",
                                  "object_type_cd": "TABLE",
                                  "schema_cd": "meta",
                                  "connection_id": "00000000-0000-0000-0000-000000000201",
                                  "registered_by": "producer",
                                  "attributes": [
                                    {
                                      "attribute_cd": "AMOUNT",
                                      "attribute_nm": "Amount",
                                      "data_type_cd": "DECIMAL",
                                      "taxonomy_cd": "MDRM12345678",
                                      "taxonomy_source_cd": "MDRM",
                                      "taxonomy_jurisdiction_cd": "US",
                                      "pk_flg": true,
                                      "fk_flg": false,
                                      "nullable_flg": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.object_id").value("00000000-0000-0000-0000-000000000101"))
                .andExpect(jsonPath("$.lifecycle_status_cd").value("DRAFT"))
                .andExpect(jsonPath("$.workflow_status_cd").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.attributes[0].attribute_cd").value("AMOUNT"))
                .andExpect(jsonPath("$.attributes[0].pk_flg").value(true))
                .andExpect(jsonPath("$.attributes[0].fk_flg").value(false))
                .andExpect(jsonPath("$.attributes[0].nullable_flg").value(false));

        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("GL Balance", service.lastRequest.object_nm());
        assertEquals("Amount", service.lastRequest.attributes().get(0).attribute_nm());
        assertTrue(service.lastRequest.attributes().get(0).pk_flg());
        assertEquals(false, service.lastRequest.attributes().get(0).fk_flg());
        assertEquals(false, service.lastRequest.attributes().get(0).nullable_flg());
    }

    @Test
    void rejectsObjectNameLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingObjectRegistrationService());

        mockMvc.perform(post("/api/objects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "object_cd": "GL_BALANCE",
                                  "object_nm": "123456789012345678901234567890123",
                                  "object_type_cd": "TABLE",
                                  "schema_cd": "meta",
                                  "connection_id": "00000000-0000-0000-0000-000000000201",
                                  "registered_by": "producer",
                                  "attributes": [
                                    {
                                      "attribute_cd": "AMOUNT",
                                      "attribute_nm": "Amount",
                                      "data_type_cd": "DECIMAL",
                                      "taxonomy_cd": "MDRM12345678",
                                      "taxonomy_source_cd": "MDRM",
                                      "taxonomy_jurisdiction_cd": "US",
                                      "pk_flg": true,
                                      "fk_flg": false,
                                      "nullable_flg": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsServiceErrorsToInternalServerError() throws Exception {
        RecordingObjectRegistrationService service = new RecordingObjectRegistrationService();
        service.error = new ObjectRegistrationServiceException("registration failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/objects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "object_cd": "GL_BALANCE",
                                  "object_nm": "GL Balance",
                                  "object_type_cd": "TABLE",
                                  "schema_cd": "meta",
                                  "connection_id": "00000000-0000-0000-0000-000000000201",
                                  "registered_by": "producer",
                                  "attributes": [
                                    {
                                      "attribute_cd": "AMOUNT",
                                      "attribute_nm": "Amount",
                                      "data_type_cd": "DECIMAL",
                                      "taxonomy_cd": "MDRM12345678",
                                      "taxonomy_source_cd": "MDRM",
                                      "taxonomy_jurisdiction_cd": "US",
                                      "pk_flg": true,
                                      "fk_flg": false,
                                      "nullable_flg": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsPolicyViolationToUnprocessableEntityWithCode() throws Exception {
        RecordingObjectRegistrationService service = new RecordingObjectRegistrationService();
        service.error = new PolicyViolationException("taxonomy.jurisdiction_valid", "Taxonomy jurisdiction is invalid");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/objects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "object_cd": "GL_BALANCE",
                                  "object_nm": "GL Balance",
                                  "object_type_cd": "TABLE",
                                  "schema_cd": "meta",
                                  "connection_id": "00000000-0000-0000-0000-000000000201",
                                  "registered_by": "producer",
                                  "attributes": [
                                    {
                                      "attribute_cd": "AMOUNT",
                                      "attribute_nm": "Amount",
                                      "data_type_cd": "DECIMAL",
                                      "taxonomy_cd": "MDRM12345678",
                                      "taxonomy_source_cd": "MDRM",
                                      "taxonomy_jurisdiction_cd": "US",
                                      "pk_flg": true,
                                      "fk_flg": false,
                                      "nullable_flg": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("taxonomy.jurisdiction_valid"))
                .andExpect(jsonPath("$.message").value("Taxonomy jurisdiction is invalid"));
    }

    private static MockMvc mockMvc(ObjectRegistrationService service) {
        ObjectRegistrationController controller = new ObjectRegistrationController(service, new NoOpObjectExposureReadService());
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingObjectRegistrationService implements ObjectRegistrationService {

        private ObjectRegistrationRequestDto lastRequest;
        private ObjectRegistrationResponseDto response;
        private RuntimeException error;

        @Override
        public ObjectRegistrationResponseDto registerObject(ObjectRegistrationRequestDto request) {
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }

    private static final class NoOpObjectExposureReadService implements ObjectExposureReadService {

        @Override
        public List<ObjectExposureSummaryDto> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            throw new UnsupportedOperationException("Not used in write tests");
        }

        @Override
        public ObjectExposureDetailDto findObject(String clientId, UUID objectId) {
            throw new UnsupportedOperationException("Not used in write tests");
        }
    }
}
