package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupEffectiveReviewReadDao;
import com.lextr.semanticlayer.dao.FilterLookupExecutionLogWriteDao;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupPreviewServiceException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogRecord;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.FilterLookupValueCountRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterLookupPreviewServiceImplTest {

    @Test
    void returnsManualPreviewValuesAndLogsSuccessfulExecution() {
        RecordingFilterLookupEffectiveReviewReadDao readDao = new RecordingFilterLookupEffectiveReviewReadDao();
        readDao.lookup = Optional.of(manualLookup());
        readDao.previewValues = List.of(previewValue());
        readDao.valueCount = new FilterLookupValueCountRecord("LEDGER_SCOPE", "client-a", 1L);
        RecordingFilterLookupExecutionLogWriteDao writeDao = new RecordingFilterLookupExecutionLogWriteDao();
        writeDao.recordToReturn = successLogRecord();
        FilterLookupPreviewServiceImpl service = new FilterLookupPreviewServiceImpl(readDao, writeDao);

        FilterLookupPreviewResponseDto result = service.previewFilterLookup(previewRequest());

        assertEquals("client-a", readDao.lastClientId);
        assertEquals("LEDGER_SCOPE", readDao.lastLookupCode);
        assertEquals("LEDGER_SCOPE", writeDao.lastRequest.lookup_cd());
        assertEquals("preview-user", writeDao.lastRequest.executed_by());
        assertEquals("SUCCESS", writeDao.lastRequest.result_status_cd());
        assertEquals("IN_LIST", writeDao.lastRequest.execution_strategy_used_cd());
        assertEquals(1, writeDao.lastRequest.phase1_row_count());
        assertEquals(801L, result.execution_log_id());
        assertEquals(1L, result.value_count());
        assertEquals("LEDGER_100", result.preview_values().get(0).value_cd());
        assertNotNull(result.phase1_duration_ms());
    }

    @Test
    void returnsNotFoundWhenLookupMissing() {
        FilterLookupPreviewServiceImpl service = new FilterLookupPreviewServiceImpl(
                new RecordingFilterLookupEffectiveReviewReadDao(),
                new RecordingFilterLookupExecutionLogWriteDao()
        );

        assertThrows(RegistryResourceNotFoundException.class, () -> service.previewFilterLookup(previewRequest()));
    }

    @Test
    void logsErrorAndFailsForUnsupportedConstructionType() {
        RecordingFilterLookupEffectiveReviewReadDao readDao = new RecordingFilterLookupEffectiveReviewReadDao();
        readDao.lookup = Optional.of(sqlLookup());
        RecordingFilterLookupExecutionLogWriteDao writeDao = new RecordingFilterLookupExecutionLogWriteDao();
        writeDao.recordToReturn = errorLogRecord();
        FilterLookupPreviewServiceImpl service = new FilterLookupPreviewServiceImpl(readDao, writeDao);

        FilterLookupPreviewServiceException exception = assertThrows(
                FilterLookupPreviewServiceException.class,
                () -> service.previewFilterLookup(previewRequest())
        );

        assertEquals("ERROR", writeDao.lastRequest.result_status_cd());
        assertEquals("SQL_DISTINCT", writeDao.lastRequest.execution_strategy_used_cd());
        assertEquals(0, writeDao.lastRequest.phase1_row_count());
        assertEquals("Preview execution is not configured for construction type SQL_QUERY", exception.getMessage());
    }

    private static FilterLookupPreviewRequestDto previewRequest() {
        return new FilterLookupPreviewRequestDto("client-a", "LEDGER_SCOPE", "preview-user");
    }

    private static SemanticFilterLookupRecord manualLookup() {
        return new SemanticFilterLookupRecord(
                101L,
                "LEDGER_SCOPE",
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
                120,
                true,
                true,
                false,
                false,
                "Ledger scope values",
                "client-a",
                "REVIEW",
                "PENDING",
                null,
                null,
                LocalDate.parse("2026-09-16"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer"
        );
    }

    private static SemanticFilterLookupRecord sqlLookup() {
        return new SemanticFilterLookupRecord(
                102L,
                "LEDGER_SCOPE",
                "SQL_QUERY",
                null,
                "meta.gl_balance",
                null,
                "ledger_id",
                null,
                null,
                null,
                "SQL_DISTINCT",
                500,
                10000,
                60,
                null,
                true,
                true,
                false,
                false,
                "Ledger scope values",
                "client-a",
                "REVIEW",
                "PENDING",
                null,
                null,
                LocalDate.parse("2026-09-16"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer"
        );
    }

    private static FilterLookupPreviewValueRecord previewValue() {
        return new FilterLookupPreviewValueRecord(
                "LEDGER_SCOPE",
                "client-a",
                "LEDGER_100",
                "Ledger 100",
                "ACTIVE",
                true,
                LocalDate.parse("2026-07-01"),
                "WKFL-100",
                OffsetDateTime.parse("2026-06-18T10:00:00Z"),
                14,
                "Pending activation",
                "producer",
                OffsetDateTime.parse("2026-06-18T09:00:00Z"),
                "reviewer",
                OffsetDateTime.parse("2026-06-18T11:00:00Z"),
                OffsetDateTime.parse("2026-06-18T12:00:00Z")
        );
    }

    private static FilterLookupExecutionLogRecord successLogRecord() {
        return new FilterLookupExecutionLogRecord(
                801L,
                "LEDGER_SCOPE",
                "preview-user",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                18,
                1,
                false,
                "IN_LIST",
                null,
                "SUCCESS",
                null,
                null
        );
    }

    private static FilterLookupExecutionLogRecord errorLogRecord() {
        return new FilterLookupExecutionLogRecord(
                802L,
                "LEDGER_SCOPE",
                "preview-user",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                4,
                0,
                false,
                "SQL_DISTINCT",
                null,
                "ERROR",
                "Preview execution is not configured for construction type SQL_QUERY",
                null
        );
    }

    private static final class RecordingFilterLookupEffectiveReviewReadDao implements FilterLookupEffectiveReviewReadDao {

        private Optional<SemanticFilterLookupRecord> lookup = Optional.empty();
        private List<FilterLookupPreviewValueRecord> previewValues = List.of();
        private FilterLookupValueCountRecord valueCount = new FilterLookupValueCountRecord("LEDGER_SCOPE", "client-a", 0L);
        private String lastClientId;
        private String lastLookupCode;

        @Override
        public Optional<SemanticFilterLookupRecord> findLookupByCode(String clientId, String lookupCode) {
            lastClientId = clientId;
            lastLookupCode = lookupCode;
            return lookup;
        }

        @Override
        public List<FilterLookupPreviewValueRecord> findManualValuesByLookup(String clientId, String lookupCode) {
            lastClientId = clientId;
            lastLookupCode = lookupCode;
            return previewValues;
        }

        @Override
        public FilterLookupValueCountRecord countValuesByLookup(String clientId, String lookupCode) {
            lastClientId = clientId;
            lastLookupCode = lookupCode;
            return valueCount;
        }
    }

    private static final class RecordingFilterLookupExecutionLogWriteDao implements FilterLookupExecutionLogWriteDao {

        private FilterLookupExecutionLogWriteRequest lastRequest;
        private FilterLookupExecutionLogRecord recordToReturn;

        @Override
        public FilterLookupExecutionLogRecord insertExecutionLog(FilterLookupExecutionLogWriteRequest request) {
            lastRequest = request;
            return recordToReturn;
        }
    }
}
