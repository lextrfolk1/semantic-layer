package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupReadService;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import com.lextr.semanticlayer.service.impl.FilterLookupReadServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilterLookupReadControllerTest {

    @Test
    void appliesFiltersAndClientScoping() throws Exception {
        RecordingFilterLookupReadDao dao = new RecordingFilterLookupReadDao();
        dao.lookupsByClient = Map.of(
                "client-a", List.of(filterLookupRecord("LEDGER_SCOPE", 45, "REVIEW", "PENDING", "ACTIVE"))
        );
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/filter-lookups")
                        .queryParam("client_id", "client-a")
                        .queryParam("governance_status_cd", "REVIEW")
                        .queryParam("health_status_cd", "PENDING")
                        .queryParam("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$[0].effective_review_period_days").value(45))
                .andExpect(jsonPath("$[0].effective_review_period_source_cd").value("LOOKUP_OVERRIDE"))
                .andExpect(jsonPath("$[0].value_count").value(2));

        assertEquals("client-a", dao.lastClientId);
        assertEquals("REVIEW", dao.lastGovernanceStatusCode);
        assertEquals("PENDING", dao.lastHealthStatusCode);
        assertEquals("ACTIVE", dao.lastLifecycleStatusCode);
    }

    @Test
    void returnsLookupDetailWithinClientScope() throws Exception {
        RecordingFilterLookupReadDao dao = new RecordingFilterLookupReadDao();
        dao.lookupsByClient = Map.of(
                "client-a", List.of(filterLookupRecord("LEDGER_SCOPE", null, "REVIEW", "PENDING", "ACTIVE"))
        );
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/filter-lookups/{lookup_code}", "LEDGER_SCOPE")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.effective_review_period_days").value(90))
                .andExpect(jsonPath("$.effective_review_period_source_cd").value("GOV_DEFAULT"))
                .andExpect(jsonPath("$.value_count").value(2));

        assertEquals("client-a", dao.lastLookupClientId);
        assertEquals("LEDGER_SCOPE", dao.lastLookupCode);
    }

    @Test
    void returns404ForUnknownLookupWithinClientScope() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingFilterLookupReadDao());

        mockMvc.perform(get("/api/filter-lookups/{lookup_code}", "UNKNOWN_LOOKUP")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(FilterLookupReadDao dao) {
        FilterLookupReadService readService = new FilterLookupReadServiceImpl(dao, new FixedGovernancePolicyPresetReadDao());
        FilterLookupRegistrationController controller =
                new FilterLookupRegistrationController(new NoOpFilterLookupRegistrationService(), readService);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static SemanticFilterLookupRecord filterLookupRecord(String lookupCode,
                                                                 Integer overrideDays,
                                                                 String governanceStatusCode,
                                                                 String healthStatusCode,
                                                                 String lifecycleStatusCode) {
        return new SemanticFilterLookupRecord(
                101L,
                lookupCode,
                "MANUAL_LIST",
                "HAND_TYPED",
                "meta.gl_balance",
                "ledger_status = 'ACTIVE'",
                "ledger_id",
                "meta.ledger",
                "ledger_id",
                "ledger_id",
                "IN_LIST",
                500,
                10000,
                60,
                overrideDays,
                true,
                true,
                false,
                false,
                "Ledger scope values",
                "client-a",
                governanceStatusCode,
                healthStatusCode,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "certifier",
                LocalDate.parse("2026-08-02"),
                lifecycleStatusCode,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
    }

    private static final class RecordingFilterLookupReadDao implements FilterLookupReadDao {

        private Map<String, List<SemanticFilterLookupRecord>> lookupsByClient = Map.of();
        private String lastClientId;
        private String lastGovernanceStatusCode;
        private String lastHealthStatusCode;
        private String lastLifecycleStatusCode;
        private String lastLookupClientId;
        private String lastLookupCode;

        @Override
        public List<SemanticFilterLookupRecord> findLookups(String clientId,
                                                            String governanceStatusCode,
                                                            String healthStatusCode,
                                                            String lifecycleStatusCode) {
            lastClientId = clientId;
            lastGovernanceStatusCode = governanceStatusCode;
            lastHealthStatusCode = healthStatusCode;
            lastLifecycleStatusCode = lifecycleStatusCode;
            return lookupsByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> governanceStatusCode == null || governanceStatusCode.equals(record.governance_status_cd()))
                    .filter(record -> healthStatusCode == null || healthStatusCode.equals(record.health_status_cd()))
                    .filter(record -> lifecycleStatusCode == null || lifecycleStatusCode.equals(record.lifecycle_status_cd()))
                    .toList();
        }

        @Override
        public Optional<SemanticFilterLookupRecord> findLookup(String clientId, String lookupCode) {
            lastLookupClientId = clientId;
            lastLookupCode = lookupCode;
            return lookupsByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> lookupCode.equals(record.lookup_cd()))
                    .findFirst();
        }

        @Override
        public List<FilterLookupPreviewValueRecord> findManualValues(String clientId, String lookupCode) {
            return List.of();
        }

        @Override
        public long countValues(String clientId, String lookupCode) {
            return 2L;
        }
    }

    private static final class FixedGovernancePolicyPresetReadDao implements GovernancePolicyPresetReadDao {

        @Override
        public Optional<GovernancePolicyPresetRecord> findPolicyPreset(String policyCode,
                                                                       String policyScopeCode,
                                                                       LocalDate asOfDate) {
            return Optional.of(new GovernancePolicyPresetRecord(
                    policyCode,
                    "Minimum review frequency (floor, days)",
                    policyScopeCode,
                    "90",
                    "INTEGER",
                    true,
                    true,
                    LocalDate.parse("2026-01-01"),
                    null,
                    "governance-owner",
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    "governance-owner"
            ));
        }

        @Override
        public java.util.List<GovernancePolicyPresetRecord> findPolicyPresets(String policyScopeCode, LocalDate asOfDate) {
            return java.util.List.of(new GovernancePolicyPresetRecord(
                    "GOV-FL-001",
                    "Minimum review frequency (floor, days)",
                    policyScopeCode,
                    "90",
                    "INTEGER",
                    true,
                    true,
                    LocalDate.parse("2026-01-01"),
                    null,
                    "governance-owner",
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    "governance-owner"
            ));
        }
    }

    private static final class NoOpFilterLookupRegistrationService implements FilterLookupRegistrationService {

        @Override
        public FilterLookupRegistrationResponseDto registerFilterLookup(FilterLookupRegistrationRequestDto request) {
            throw new UnsupportedOperationException("Not used in read tests");
        }
    }
}
