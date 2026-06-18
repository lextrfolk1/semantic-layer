package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcFilterLookupSqlPreviewClientTest {

    @Test
    void delegatesSqlPreviewReadsToFilterLookupReadDao() {
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        JdbcFilterLookupSqlPreviewClient client = new JdbcFilterLookupSqlPreviewClient(readDao);
        SemanticFilterLookupRecord lookup = FilterLookupPreviewFixtures.sqlLookup("SQL_LEDGER_SCOPE");

        List<FilterLookupPreviewValueDto> result = client.previewDistinctValues("client-a", lookup);

        assertEquals("client-a", readDao.recordedClientId);
        assertEquals("SQL_LEDGER_SCOPE", readDao.recordedLookup.lookup_cd());
        assertEquals(1, result.size());
        assertEquals("LEDGER_100", result.get(0).value_cd());
        assertEquals("Ledger 100", result.get(0).value_desc());
    }

    private static final class RecordingFilterLookupReadDao implements FilterLookupReadDao {

        private String recordedClientId;
        private SemanticFilterLookupRecord recordedLookup;

        @Override
        public List<SemanticFilterLookupRecord> findLookups(String clientId,
                                                            String governanceStatusCode,
                                                            String healthStatusCode,
                                                            String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public java.util.Optional<SemanticFilterLookupRecord> findLookup(String clientId, String lookupCode) {
            return java.util.Optional.empty();
        }

        @Override
        public List<FilterLookupPreviewValueRecord> findManualValues(String clientId, String lookupCode) {
            return List.of();
        }

        @Override
        public List<FilterLookupPreviewValueRecord> findSqlValues(String clientId, SemanticFilterLookupRecord lookup) {
            this.recordedClientId = clientId;
            this.recordedLookup = lookup;
            return List.of(new FilterLookupPreviewValueRecord(
                    lookup.lookup_cd(),
                    clientId,
                    "LEDGER_100",
                    "Ledger 100",
                    "ACTIVE",
                    true,
                    null,
                    null,
                    OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }

        @Override
        public long countValues(String clientId, String lookupCode) {
            return 0;
        }
    }
}
