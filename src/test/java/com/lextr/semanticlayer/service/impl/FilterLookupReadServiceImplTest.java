package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterLookupReadServiceImplTest {

    @Test
    void mapsLookupOverrideAsEffectiveReviewPeriod() {
        FilterLookupReadServiceImpl service = new FilterLookupReadServiceImpl(
                new FixedFilterLookupReadDao(List.of(filterLookupRecord("LEDGER_SCOPE", 45)), 3L),
                fixedGovernanceDao("90")
        );

        List<FilterLookupEffectiveReviewDto> results = service.findLookups("client-a", "REVIEW", "PENDING", "ACTIVE");

        assertEquals(1, results.size());
        assertEquals(Integer.valueOf(45), results.get(0).effective_review_period_days());
        assertEquals("LOOKUP_OVERRIDE", results.get(0).effective_review_period_source_cd());
        assertEquals(3L, results.get(0).value_count());
    }

    @Test
    void fallsBackToGovernanceDefaultWhenOverrideMissing() {
        FilterLookupReadServiceImpl service = new FilterLookupReadServiceImpl(
                new FixedFilterLookupReadDao(List.of(filterLookupRecord("LEDGER_SCOPE", null)), 0L),
                fixedGovernanceDao("90")
        );

        FilterLookupEffectiveReviewDto result = service.findLookup("client-a", "LEDGER_SCOPE");

        assertEquals(Integer.valueOf(90), result.effective_review_period_days());
        assertEquals("GOV_DEFAULT", result.effective_review_period_source_cd());
        assertEquals(0L, result.value_count());
    }

    @Test
    void enforcesGovernanceFloorWhenStoredOverrideIsLooserThanCurrentPolicy() {
        FilterLookupReadServiceImpl service = new FilterLookupReadServiceImpl(
                new FixedFilterLookupReadDao(List.of(filterLookupRecord("LEDGER_SCOPE", 120)), 5L),
                fixedGovernanceDao("90")
        );

        FilterLookupEffectiveReviewDto result = service.findLookup("client-a", "LEDGER_SCOPE");

        assertEquals(Integer.valueOf(90), result.effective_review_period_days());
        assertEquals("GOV_ENFORCED", result.effective_review_period_source_cd());
    }

    @Test
    void returns404WhenLookupMissing() {
        FilterLookupReadServiceImpl service = new FilterLookupReadServiceImpl(
                new FixedFilterLookupReadDao(List.of(), 0L),
                fixedGovernanceDao("90")
        );

        assertThrows(RegistryResourceNotFoundException.class, () -> service.findLookup("client-a", "UNKNOWN_LOOKUP"));
    }

    @Test
    void failsWhenGovernancePolicyMissing() {
        FilterLookupReadServiceImpl service = new FilterLookupReadServiceImpl(
                new FixedFilterLookupReadDao(List.of(filterLookupRecord("LEDGER_SCOPE", 45)), 1L),
                (policyCode, policyScopeCode, asOfDate) -> Optional.empty()
        );

        assertThrows(SemanticLayerException.class,
                () -> service.findLookups("client-a", "REVIEW", "PENDING", "ACTIVE"));
    }

    @Test
    void failsWhenGovernancePolicyValueCannotBeParsed() {
        FilterLookupReadServiceImpl service = new FilterLookupReadServiceImpl(
                new FixedFilterLookupReadDao(List.of(filterLookupRecord("LEDGER_SCOPE", 45)), 1L),
                fixedGovernanceDao("not-a-number")
        );

        assertThrows(SemanticLayerException.class, () -> service.findLookup("client-a", "LEDGER_SCOPE"));
    }

    private static SemanticFilterLookupRecord filterLookupRecord(String lookupCode, Integer overrideDays) {
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
                "REVIEW",
                "PENDING",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "certifier",
                LocalDate.parse("2026-08-02"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
    }

    private static GovernancePolicyPresetReadDao fixedGovernanceDao(String value) {
        return (policyCode, policyScopeCode, asOfDate) -> Optional.of(new GovernancePolicyPresetRecord(
                policyCode,
                "Minimum review frequency (floor, days)",
                policyScopeCode,
                value,
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

    private static final class FixedFilterLookupReadDao implements FilterLookupReadDao {

        private final List<SemanticFilterLookupRecord> lookups;
        private final long valueCount;

        private FixedFilterLookupReadDao(List<SemanticFilterLookupRecord> lookups, long valueCount) {
            this.lookups = lookups;
            this.valueCount = valueCount;
        }

        @Override
        public List<SemanticFilterLookupRecord> findLookups(String clientId,
                                                            String governanceStatusCode,
                                                            String healthStatusCode,
                                                            String lifecycleStatusCode) {
            return lookups;
        }

        @Override
        public Optional<SemanticFilterLookupRecord> findLookup(String clientId, String lookupCode) {
            return lookups.stream().filter(record -> lookupCode.equals(record.lookup_cd())).findFirst();
        }

        @Override
        public long countValues(String clientId, String lookupCode) {
            return valueCount;
        }
    }
}
