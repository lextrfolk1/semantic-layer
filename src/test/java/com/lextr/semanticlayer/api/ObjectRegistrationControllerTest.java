package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.AttributeRegistrationResponseDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.exception.ObjectRegistrationServiceException;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                        "US"
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
                                      "taxonomy_jurisdiction_cd": "US"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.object_id").value("00000000-0000-0000-0000-000000000101"))
                .andExpect(jsonPath("$.lifecycle_status_cd").value("DRAFT"))
                .andExpect(jsonPath("$.workflow_status_cd").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.attributes[0].attribute_cd").value("AMOUNT"));

        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("GL Balance", service.lastRequest.object_nm());
        assertEquals("Amount", service.lastRequest.attributes().get(0).attribute_nm());
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
                                      "taxonomy_jurisdiction_cd": "US"
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
                                      "taxonomy_jurisdiction_cd": "US"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isInternalServerError());
    }

    private static MockMvc mockMvc(ObjectRegistrationService service) {
        ObjectRegistrationController controller = new ObjectRegistrationController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
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
}
