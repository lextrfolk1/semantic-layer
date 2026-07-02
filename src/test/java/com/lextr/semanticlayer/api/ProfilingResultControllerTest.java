package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.ProfilingResultDto;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.service.ProfilingResultReadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfilingResultControllerTest {

    @Test
    void routesMetricsEndpointToService() throws Exception {
        RecordingProfilingResultReadService service = new RecordingProfilingResultReadService();
        service.responses = List.of(new ProfilingResultDto(
                701L,
                "client-a",
                "meta",
                "gl_balance",
                "ledger_id",
                "AMOUNT",
                5,
                80,
                "READY",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T10:15:31Z"),
                "profiler",
                OffsetDateTime.parse("2026-06-18T10:15:32Z"),
                "profiler"
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/profiling/metrics")
                        .queryParam("client_id", "client-a")
                        .queryParam("object_id", UUID.fromString("00000000-0000-0000-0000-000000000101").toString())
                        .queryParam("profiling_status_cd", "READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].object_cd").value("gl_balance"))
                .andExpect(jsonPath("$[0].profiling_status").value("READY"));

        assertEquals("client-a", service.lastClientId);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000101"), service.lastObjectId);
        assertEquals("READY", service.lastProfilingStatusCode);
    }

    @Test
    void mapsServiceErrorsToInternalServerError() throws Exception {
        RecordingProfilingResultReadService service = new RecordingProfilingResultReadService();
        service.error = new SemanticLayerException("profiling lookup failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/profiling/metrics")
                        .queryParam("client_id", "client-a")
                        .queryParam("object_id", UUID.fromString("00000000-0000-0000-0000-000000000101").toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("profiling lookup failed"));
    }

    private static MockMvc mockMvc(ProfilingResultReadService service) {
        ProfilingResultController controller = new ProfilingResultController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingProfilingResultReadService implements ProfilingResultReadService {

        private List<ProfilingResultDto> responses = List.of();
        private String lastClientId;
        private UUID lastObjectId;
        private String lastProfilingStatusCode;
        private RuntimeException error;

        @Override
        public List<ProfilingResultDto> findMetrics(String clientId, UUID objectId, String profilingStatusCode) {
            lastClientId = clientId;
            lastObjectId = objectId;
            lastProfilingStatusCode = profilingStatusCode;
            if (error != null) {
                throw error;
            }
            return responses;
        }
    }
}
