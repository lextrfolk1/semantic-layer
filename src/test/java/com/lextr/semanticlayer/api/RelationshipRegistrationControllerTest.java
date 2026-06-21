package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RelationshipRegistrationServiceException;
import com.lextr.semanticlayer.service.RelationshipRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelationshipRegistrationControllerTest {

    @Test
    void routesRelationshipRegistrationEndpointToService() throws Exception {
        RecordingRelationshipRegistrationService service = new RecordingRelationshipRegistrationService();
        service.response = new RelationshipRegistrationResponseDto(
                101L,
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                null,
                "ACTIVE"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/relationships")
                        .contentType("application/json")
                        .content("""
                                {
                                  "relationship_cd": "GL_TO_LEDGER",
                                  "parent_schema_cd": "meta",
                                  "parent_object_cd": "gl_balance",
                                  "parent_attribute_cd": "ledger_id",
                                  "child_schema_cd": "meta",
                                  "child_object_cd": "ledger",
                                  "child_attribute_cd": "ledger_id",
                                  "relationship_type_cd": "FOREIGN_KEY",
                                  "cardinality_cd": "MANY_TO_ONE",
                                  "join_type_cd": "INNER",
                                  "is_enforced_flg": true,
                                  "is_nullable_flg": false,
                                  "is_cross_engine_flg": false,
                                  "relationship_desc": "GL balances map to ledger master rows",
                                  "ai_join_guidance_txt": "Join on ledger identifier",
                                  "registered_by": "producer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.relationship_cd").value("GL_TO_LEDGER"))
                .andExpect(jsonPath("$.relationship_type_cd").value("FOREIGN_KEY"))
                .andExpect(jsonPath("$.lifecycle_status_cd").value("ACTIVE"));

        assertEquals("GL_TO_LEDGER", service.lastRequest.relationship_cd());
        assertEquals("ledger_id", service.lastRequest.parent_attribute_cd());
        assertEquals("producer", service.lastRequest.registered_by());
    }

    @Test
    void rejectsAttributeCodeLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingRelationshipRegistrationService());

        mockMvc.perform(post("/api/relationships")
                        .contentType("application/json")
                        .content("""
                                {
                                  "relationship_cd": "GL_TO_LEDGER",
                                  "parent_schema_cd": "meta",
                                  "parent_object_cd": "gl_balance",
                                  "parent_attribute_cd": "123456789012345678901234567890123",
                                  "child_schema_cd": "meta",
                                  "child_object_cd": "ledger",
                                  "child_attribute_cd": "ledger_id",
                                  "relationship_type_cd": "FOREIGN_KEY",
                                  "cardinality_cd": "MANY_TO_ONE",
                                  "join_type_cd": "INNER",
                                  "is_enforced_flg": true,
                                  "is_nullable_flg": false,
                                  "is_cross_engine_flg": false,
                                  "registered_by": "producer"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsServiceErrorsToInternalServerError() throws Exception {
        RecordingRelationshipRegistrationService service = new RecordingRelationshipRegistrationService();
        service.error = new RelationshipRegistrationServiceException("registration failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/relationships")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsPolicyViolationToUnprocessableEntityWithCode() throws Exception {
        RecordingRelationshipRegistrationService service = new RecordingRelationshipRegistrationService();
        service.error = new PolicyViolationException("POL-CE-001", "Cross-engine relationships are not allowed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/relationships")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-CE-001"))
                .andExpect(jsonPath("$.message").value("Cross-engine relationships are not allowed"));
    }

    private static MockMvc mockMvc(RelationshipRegistrationService service) {
        RelationshipRegistrationController controller = new RelationshipRegistrationController(
                service,
                providerOf(null),
                null
        );
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
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

    private static String validRequestJson() {
        return """
                {
                  "relationship_cd": "GL_TO_LEDGER",
                  "parent_schema_cd": "meta",
                  "parent_object_cd": "gl_balance",
                  "parent_attribute_cd": "ledger_id",
                  "child_schema_cd": "meta",
                  "child_object_cd": "ledger",
                  "child_attribute_cd": "ledger_id",
                  "relationship_type_cd": "FOREIGN_KEY",
                  "cardinality_cd": "MANY_TO_ONE",
                  "join_type_cd": "INNER",
                  "is_enforced_flg": true,
                  "is_nullable_flg": false,
                  "is_cross_engine_flg": false,
                  "relationship_desc": "GL balances map to ledger master rows",
                  "ai_join_guidance_txt": "Join on ledger identifier",
                  "registered_by": "producer"
                }
                """;
    }

    private static final class RecordingRelationshipRegistrationService implements RelationshipRegistrationService {

        private RelationshipRegistrationRequestDto lastRequest;
        private RelationshipRegistrationResponseDto response;
        private RuntimeException error;

        @Override
        public RelationshipRegistrationResponseDto registerRelationship(RelationshipRegistrationRequestDto request) {
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
