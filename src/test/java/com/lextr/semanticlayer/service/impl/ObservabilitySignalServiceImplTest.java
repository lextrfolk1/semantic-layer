package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dto.ObservabilitySignalCorrelationRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalIngestRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalResponseDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.ObservabilitySignalCorrelationWriteRequest;
import com.lextr.semanticlayer.model.ObservabilitySignalRecord;
import com.lextr.semanticlayer.model.ObservabilitySignalWriteRequest;
import com.lextr.semanticlayer.service.ObservabilitySignalService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservabilitySignalServiceImplTest {

    @Test
    void defaultsMissingSeverityAndStatusBeforePersistingSignal() {
        RecordingObservabilitySignalDao dao = new RecordingObservabilitySignalDao();
        dao.insertedRecord = record(501L, "OPEN", "INFO");
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(dao);

        ObservabilitySignalResponseDto response = service.ingestSignal(new ObservabilitySignalIngestRequestDto(
                "client-a",
                "FRESHNESS",
                null,
                null,
                "PIPELINE",
                "DATASET",
                "orders",
                "orders#2026-06-18",
                "Freshness lag detected",
                "Latest event lagged by 4h",
                true,
                "Re-run ETL",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "tooling"
        ));

        assertEquals("INFO", dao.lastInsertRequest.severity_cd());
        assertEquals("OPEN", dao.lastInsertRequest.signal_status_cd());
        assertEquals("tooling", dao.lastInsertRequest.created_by());
        assertEquals(501L, response.id());
        assertEquals("OPEN", response.signal_status_cd());
        assertEquals("INFO", response.severity_cd());
    }

    @Test
    void findSignalsDelegatesFiltersAndMapsResults() {
        RecordingObservabilitySignalDao dao = new RecordingObservabilitySignalDao();
        dao.signals = List.of(record(501L, "OPEN", "WARN"));
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(dao);

        List<ObservabilitySignalResponseDto> responses = service.findSignals(
                "client-a",
                "FRESHNESS",
                "WARN",
                "OPEN",
                "orders#2026-06-18"
        );

        assertEquals("client-a", dao.lastClientId);
        assertEquals("FRESHNESS", dao.lastSignalTypeCode);
        assertEquals("WARN", dao.lastSeverityCode);
        assertEquals("OPEN", dao.lastSignalStatusCode);
        assertEquals("orders#2026-06-18", dao.lastCorrelationKeyText);
        assertEquals(1, responses.size());
        assertEquals(501L, responses.get(0).id());
    }

    @Test
    void correlateSignalReturnsUpdatedRecord() {
        RecordingObservabilitySignalDao dao = new RecordingObservabilitySignalDao();
        dao.correlatedRecord = Optional.of(record(501L, "TRIAGE", "WARN"));
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(dao);

        ObservabilitySignalResponseDto response = service.correlateSignal(501L, new ObservabilitySignalCorrelationRequestDto(
                "client-a",
                "TRIAGE",
                701L,
                true,
                "Create DQ rerun",
                OffsetDateTime.parse("2026-06-18T10:20:30Z"),
                OffsetDateTime.parse("2026-06-18T11:20:30Z"),
                "analyst"
        ));

        assertEquals(501L, dao.lastCorrelationRequest.id());
        assertEquals("TRIAGE", dao.lastCorrelationRequest.signal_status_cd());
        assertEquals(701L, dao.lastCorrelationRequest.workflow_task_id());
        assertEquals("analyst", dao.lastCorrelationRequest.updated_by());
        assertEquals("TRIAGE", response.signal_status_cd());
    }

    @Test
    void correlateSignalMapsMissingRowsToNotFound() {
        RecordingObservabilitySignalDao dao = new RecordingObservabilitySignalDao();
        dao.correlatedRecord = Optional.empty();
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(dao);

        assertThrows(RegistryResourceNotFoundException.class, () -> service.correlateSignal(501L, new ObservabilitySignalCorrelationRequestDto(
                "client-a",
                "TRIAGE",
                null,
                false,
                null,
                null,
                null,
                "analyst"
        )));
    }

    private static ObservabilitySignalRecord record(Long id, String signalStatusCode, String severityCode) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        return new ObservabilitySignalRecord(
                id,
                "client-a",
                "FRESHNESS",
                severityCode,
                signalStatusCode,
                "PIPELINE",
                "DATASET",
                "orders",
                "orders#2026-06-18",
                "Freshness lag detected",
                "Latest event lagged by 4h",
                timestamp,
                null,
                null,
                701L,
                true,
                "Create DQ rerun",
                timestamp,
                "tooling",
                timestamp,
                "tooling"
        );
    }

    private static final class RecordingObservabilitySignalDao implements com.lextr.semanticlayer.dao.ObservabilitySignalDao {

        private ObservabilitySignalWriteRequest lastInsertRequest;
        private ObservabilitySignalCorrelationWriteRequest lastCorrelationRequest;
        private String lastClientId;
        private String lastSignalTypeCode;
        private String lastSeverityCode;
        private String lastSignalStatusCode;
        private String lastCorrelationKeyText;
        private List<ObservabilitySignalRecord> signals = new ArrayList<>();
        private ObservabilitySignalRecord insertedRecord;
        private Optional<ObservabilitySignalRecord> correlatedRecord = Optional.empty();

        @Override
        public ObservabilitySignalRecord insertSignal(ObservabilitySignalWriteRequest request) {
            lastInsertRequest = request;
            return insertedRecord;
        }

        @Override
        public List<ObservabilitySignalRecord> findSignals(String clientId,
                                                           String signalTypeCode,
                                                           String severityCode,
                                                           String signalStatusCode,
                                                           String correlationKeyText) {
            lastClientId = clientId;
            lastSignalTypeCode = signalTypeCode;
            lastSeverityCode = severityCode;
            lastSignalStatusCode = signalStatusCode;
            lastCorrelationKeyText = correlationKeyText;
            return signals;
        }

        @Override
        public Optional<ObservabilitySignalRecord> correlateSignal(ObservabilitySignalCorrelationWriteRequest request) {
            lastCorrelationRequest = request;
            return correlatedRecord;
        }
    }
}
