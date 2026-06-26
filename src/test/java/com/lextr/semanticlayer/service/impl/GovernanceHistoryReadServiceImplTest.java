package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.GovernanceHistoryReadDao;
import com.lextr.semanticlayer.dto.GovernanceHistoryEventDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.GovernanceHistoryEventRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GovernanceHistoryReadServiceImplTest {

    @Test
    void appliesFiltersAndMapsSnakeCaseDto() {
        RecordingGovernanceHistoryReadDao dao = new RecordingGovernanceHistoryReadDao();
        dao.baseEvents = List.of(historyRecord("REGISTERED"), historyRecord("APPROVED"));
        dao.filteredEvents = List.of(historyRecord("APPROVED"));
        GovernanceHistoryReadServiceImpl service = new GovernanceHistoryReadServiceImpl(dao);

        List<GovernanceHistoryEventDto> results =
                service.findHistory("client-a", "OBJECT", "meta.gl_balance", "APPROVED");

        assertEquals(2, dao.calls.size());
        assertEquals("client-a", dao.calls.get(0).clientId());
        assertEquals("OBJECT", dao.calls.get(0).entityTypeCode());
        assertEquals("meta.gl_balance", dao.calls.get(0).entityRef());
        assertEquals(null, dao.calls.get(0).changeTypeCode());
        assertEquals("APPROVED", dao.calls.get(1).changeTypeCode());
        assertEquals(1, results.size());
        assertEquals("APPROVED", results.get(0).change_type_cd());
        assertEquals("Object registered", results.get(0).change_summary_txt());
        assertEquals("producer", results.get(0).actor_id());
    }

    @Test
    void returns404WhenHistoryMissing() {
        GovernanceHistoryReadServiceImpl service =
                new GovernanceHistoryReadServiceImpl((clientId, entityTypeCode, entityRef, changeTypeCode) -> List.of());

        assertThrows(RegistryResourceNotFoundException.class,
                () -> service.findHistory("client-a", "OBJECT", "missing", null));
    }

    @Test
    void validatesRequiredInputs() {
        GovernanceHistoryReadServiceImpl service =
                new GovernanceHistoryReadServiceImpl((clientId, entityTypeCode, entityRef, changeTypeCode) -> List.of());

        assertThrows(PolicyViolationException.class,
                () -> service.findHistory("", "OBJECT", "meta.gl_balance", null));
        assertThrows(PolicyViolationException.class,
                () -> service.findHistory("client-a", "", "meta.gl_balance", null));
        assertThrows(PolicyViolationException.class,
                () -> service.findHistory("client-a", "OBJECT", "", null));
    }

    private static GovernanceHistoryEventRecord historyRecord(String changeTypeCode) {
        return new GovernanceHistoryEventRecord(
                501L,
                "client-a",
                "OBJECT",
                "meta.gl_balance",
                changeTypeCode,
                "Object registered",
                "producer",
                OffsetDateTime.parse("2026-06-18T00:00:00Z"),
                "{\"status\":\"DRAFT\"}",
                "{\"status\":\"ACTIVE\"}"
        );
    }

    private record Call(String clientId, String entityTypeCode, String entityRef, String changeTypeCode) {
    }

    private static final class RecordingGovernanceHistoryReadDao implements GovernanceHistoryReadDao {

        private final List<Call> calls = new ArrayList<>();
        private List<GovernanceHistoryEventRecord> baseEvents = List.of();
        private List<GovernanceHistoryEventRecord> filteredEvents = List.of();

        @Override
        public List<GovernanceHistoryEventRecord> findEvents(String clientId,
                                                             String entityTypeCode,
                                                             String entityRef,
                                                             String changeTypeCode) {
            calls.add(new Call(clientId, entityTypeCode, entityRef, changeTypeCode));
            return changeTypeCode == null ? baseEvents : filteredEvents;
        }
    }
}
