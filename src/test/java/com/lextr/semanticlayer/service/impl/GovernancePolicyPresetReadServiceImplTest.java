package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.exception.GovernancePolicyPresetNotFoundException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernancePolicyPresetReadServiceImplTest {

    @Test
    void findPolicyPresetsAppliesFiltersAndValidatesClient() {
        MockGovernancePolicyPresetReadDao dao = new MockGovernancePolicyPresetReadDao();
        dao.records.add(policyPresetRecord("GOV-FL-001", "FILTER_LOOKUP"));
        GovernancePolicyPresetReadServiceImpl service = new GovernancePolicyPresetReadServiceImpl(dao);

        List<GovernancePolicyPresetDto> results = service.findPolicyPresets("client-123", "FILTER_LOOKUP", LocalDate.parse("2026-06-18"));

        assertEquals(1, results.size());
        assertEquals("GOV-FL-001", results.get(0).policy_cd());
        assertEquals("FILTER_LOOKUP", dao.lastScopeCode);
        assertEquals(LocalDate.parse("2026-06-18"), dao.lastAsOfDate);
    }

    @Test
    void findPolicyPresetsDefaultsAsOfDate() {
        MockGovernancePolicyPresetReadDao dao = new MockGovernancePolicyPresetReadDao();
        GovernancePolicyPresetReadServiceImpl service = new GovernancePolicyPresetReadServiceImpl(dao);

        service.findPolicyPresets("client-123", "FILTER_LOOKUP", null);

        assertNotNull(dao.lastAsOfDate);
    }

    @Test
    void findPolicyPresetsThrowsWhenClientIdBlank() {
        MockGovernancePolicyPresetReadDao dao = new MockGovernancePolicyPresetReadDao();
        GovernancePolicyPresetReadServiceImpl service = new GovernancePolicyPresetReadServiceImpl(dao);

        assertThrows(PolicyViolationException.class, () -> service.findPolicyPresets("", "FILTER_LOOKUP", null));
        assertThrows(PolicyViolationException.class, () -> service.findPolicyPresets(null, "FILTER_LOOKUP", null));
    }

    @Test
    void findPolicyPresetReturnsSinglePreset() {
        MockGovernancePolicyPresetReadDao dao = new MockGovernancePolicyPresetReadDao();
        dao.records.add(policyPresetRecord("GOV-FL-001", "FILTER_LOOKUP"));
        GovernancePolicyPresetReadServiceImpl service = new GovernancePolicyPresetReadServiceImpl(dao);

        GovernancePolicyPresetDto result = service.findPolicyPreset("client-123", "GOV-FL-001", "FILTER_LOOKUP", LocalDate.parse("2026-06-18"));

        assertEquals("GOV-FL-001", result.policy_cd());
        assertEquals("Policy Name", result.policy_nm());
        assertEquals("90", result.default_value_txt());
        assertEquals("INTEGER", result.data_type_cd());
        assertTrue(result.is_overrideable_flg());
        assertTrue(result.override_requires_approval_flg());
        assertEquals(LocalDate.parse("2026-01-01"), result.effective_from_dt());
        assertEquals("admin", result.approved_by());
        assertEquals(OffsetDateTime.parse("2026-01-01T00:00:00Z"), result.approved_ts());
        assertEquals(OffsetDateTime.parse("2026-01-01T00:00:00Z"), result.created_ts());
        assertEquals("admin", result.created_by());
        assertEquals("GOV-FL-001", dao.lastPolicyCode);
        assertEquals("FILTER_LOOKUP", dao.lastScopeCode);
        assertEquals(LocalDate.parse("2026-06-18"), dao.lastAsOfDate);
    }

    @Test
    void findPolicyPresetThrows404WhenNotFound() {
        MockGovernancePolicyPresetReadDao dao = new MockGovernancePolicyPresetReadDao();
        GovernancePolicyPresetReadServiceImpl service = new GovernancePolicyPresetReadServiceImpl(dao);

        assertThrows(GovernancePolicyPresetNotFoundException.class, () -> service.findPolicyPreset("client-123", "GOV-FL-999", "FILTER_LOOKUP", null));
    }

    @Test
    void findPolicyPresetThrowsWhenClientIdBlank() {
        MockGovernancePolicyPresetReadDao dao = new MockGovernancePolicyPresetReadDao();
        GovernancePolicyPresetReadServiceImpl service = new GovernancePolicyPresetReadServiceImpl(dao);

        assertThrows(PolicyViolationException.class, () -> service.findPolicyPreset("", "GOV-FL-001", "FILTER_LOOKUP", null));
        assertThrows(PolicyViolationException.class, () -> service.findPolicyPreset(null, "GOV-FL-001", "FILTER_LOOKUP", null));
    }

    private static GovernancePolicyPresetRecord policyPresetRecord(String policyCode, String scope) {
        return new GovernancePolicyPresetRecord(
                policyCode,
                "Policy Name",
                scope,
                "90",
                "INTEGER",
                true,
                true,
                LocalDate.parse("2026-01-01"),
                null,
                "admin",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                "admin"
        );
    }

    private static final class MockGovernancePolicyPresetReadDao implements GovernancePolicyPresetReadDao {
        private final List<GovernancePolicyPresetRecord> records = new ArrayList<>();
        private String lastPolicyCode;
        private String lastScopeCode;
        private LocalDate lastAsOfDate;

        @Override
        public Optional<GovernancePolicyPresetRecord> findPolicyPreset(String policyCode, String policyScopeCode, LocalDate asOfDate) {
            this.lastPolicyCode = policyCode;
            this.lastScopeCode = policyScopeCode;
            this.lastAsOfDate = asOfDate;
            return records.stream().filter(r -> r.policy_cd().equals(policyCode)).findFirst();
        }

        @Override
        public List<GovernancePolicyPresetRecord> findPolicyPresets(String policyScopeCode, LocalDate asOfDate) {
            this.lastScopeCode = policyScopeCode;
            this.lastAsOfDate = asOfDate;
            return records.stream().filter(r -> policyScopeCode == null || r.policy_scope_cd().equals(policyScopeCode)).toList();
        }
    }
}
