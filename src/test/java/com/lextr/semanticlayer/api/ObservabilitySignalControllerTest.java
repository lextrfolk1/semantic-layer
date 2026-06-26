package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.ObservabilitySignalCorrelationRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalIngestRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalResponseDto;
import com.lextr.semanticlayer.exception.ObservabilitySignalServiceException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.service.ObservabilitySignalService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObservabilitySignalControllerTest {

    @Test
    void routesIngestEndpointToService() throws Exception {
        RecordingObservabilitySignalService service = new RecordingObservabilitySignalService();
        service.response = response(101L, "OPEN", "WARN");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/observability-signals")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "signal_type_cd": "FRESHNESS",
                                  "severity_cd": "WARN",
                                  "signal_status_cd": "OPEN",
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
                .andExpect(jsonPath("$.severity_cd").value("WARN"))
                .andExpect(jsonPath("$.signal_status_cd").value("OPEN"))
                .andExpect(jsonPath("$.dq_rerun_requested_flg").value(true));

        assertEquals("client-a", service.lastIngestRequest.client_id());
        assertEquals("FRESHNESS", service.lastIngestRequest.signal_type_cd());
        assertEquals("WARN", service.lastIngestRequest.severity_cd());
        assertEquals("OPEN", service.lastIngestRequest.signal_status_cd());
        assertEquals("tooling", service.lastIngestRequest.reported_by());
    }

    @Test
    void routesListEndpointToService() throws Exception {
        RecordingObservabilitySignalService service = new RecordingObservabilitySignalService();
        service.responses = List.of(response(101L, "OPEN", "WARN"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/observability-signals")
                        .queryParam("client_id", "client-a")
                        .queryParam("signal_status_cd", "OPEN")
                        .queryParam("correlation_key_txt", "orders#2026-06-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].signal_status_cd").value("OPEN"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("OPEN", service.lastSignalStatusCode);
        assertEquals("orders#2026-06-18", service.lastCorrelationKeyText);
    }

    @Test
    void routesCorrelationEndpointToService() throws Exception {
        RecordingObservabilitySignalService service = new RecordingObservabilitySignalService();
        service.response = response(101L, "TRIAGE", "WARN");
        MockMvc mockMvc = mockMvc(service);

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
                .andExpect(jsonPath("$.signal_status_cd").value("TRIAGE"))
                .andExpect(jsonPath("$.workflow_task_id").value(701));

        assertEquals(101L, service.lastSignalId);
        assertEquals("client-a", service.lastCorrelationRequest.client_id());
        assertEquals("TRIAGE", service.lastCorrelationRequest.signal_status_cd());
        assertEquals(701L, service.lastCorrelationRequest.workflow_task_id());
        assertEquals("analyst", service.lastCorrelationRequest.correlated_by());
    }

    @Test
    void rejectsReportedByLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingObservabilitySignalService());

        mockMvc.perform(post("/api/observability-signals")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "signal_type_cd": "FRESHNESS",
                                  "source_system_cd": "PIPELINE",
                                  "detected_ts": "2026-06-18T10:15:30Z",
                                  "reported_by": "123456789012345678901234567890123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsSignalServiceErrorsToInternalServerError() throws Exception {
        RecordingObservabilitySignalService service = new RecordingObservabilitySignalService();
        service.error = new ObservabilitySignalServiceException("signal ingest failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/observability-signals")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "signal_type_cd": "FRESHNESS",
                                  "source_system_cd": "PIPELINE",
                                  "detected_ts": "2026-06-18T10:15:30Z",
                                  "reported_by": "tooling"
                                }
                                """))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsMissingSignalToNotFound() throws Exception {
        RecordingObservabilitySignalService service = new RecordingObservabilitySignalService();
        service.error = new RegistryResourceNotFoundException("observability signal", "101");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/observability-signals/{signal_id}/correlate", 101L)
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "signal_status_cd": "TRIAGE",
                                  "correlated_by": "analyst"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(ObservabilitySignalService service) {
        ObservabilitySignalController controller = new ObservabilitySignalController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static ObservabilitySignalResponseDto response(Long id, String statusCode, String severityCode) {
        return new ObservabilitySignalResponseDto(
                id,
                "client-a",
                "FRESHNESS",
                severityCode,
                statusCode,
                "PIPELINE",
                "DATASET",
                "orders",
                "orders#2026-06-18",
                "Freshness lag detected",
                "Latest event lagged by 4h",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                null,
                null,
                701L,
                true,
                "Create DQ rerun",
                OffsetDateTime.parse("2026-06-18T10:16:30Z"),
                "tooling",
                OffsetDateTime.parse("2026-06-18T10:16:30Z"),
                "tooling"
        );
    }

    private static final class RecordingObservabilitySignalService implements ObservabilitySignalService {

        private ObservabilitySignalIngestRequestDto lastIngestRequest;
        private ObservabilitySignalCorrelationRequestDto lastCorrelationRequest;
        private Long lastSignalId;
        private String lastClientId;
        private String lastSignalTypeCode;
        private String lastSeverityCode;
        private String lastSignalStatusCode;
        private String lastCorrelationKeyText;
        private List<ObservabilitySignalResponseDto> responses = List.of();
        private ObservabilitySignalResponseDto response;
        private RuntimeException error;

        @Override
        public ObservabilitySignalResponseDto ingestSignal(ObservabilitySignalIngestRequestDto request) {
            lastIngestRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }

        @Override
        public List<ObservabilitySignalResponseDto> findSignals(String clientId,
                                                                String signalTypeCode,
                                                                String severityCode,
                                                                String signalStatusCode,
                                                                String correlationKeyText) {
            lastClientId = clientId;
            lastSignalTypeCode = signalTypeCode;
            lastSeverityCode = severityCode;
            lastSignalStatusCode = signalStatusCode;
            lastCorrelationKeyText = correlationKeyText;
            if (error != null) {
                throw error;
            }
            return responses;
        }

        @Override
        public ObservabilitySignalResponseDto correlateSignal(Long signalId, ObservabilitySignalCorrelationRequestDto request) {
            lastSignalId = signalId;
            lastCorrelationRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
