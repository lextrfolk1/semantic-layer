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
        dao.baseEvents = List.of(
                historyRecord("OBJECT", "meta.gl_balance", "REGISTERED", OffsetDateTime.parse("2026-06-18T00:00:00Z")),
                historyRecord("OBJECT", "meta.gl_balance", "APPROVED", OffsetDateTime.parse("2026-06-19T00:00:00Z"))
        );
        dao.filteredEvents = List.of(
                historyRecord("OBJECT", "meta.gl_balance", "APPROVED", OffsetDateTime.parse("2026-06-19T00:00:00Z"))
        );
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
    void preservesNewestFirstTimelineOrderWhenNoFilterApplied() {
        RecordingGovernanceHistoryReadDao dao = new RecordingGovernanceHistoryReadDao();
        dao.baseEvents = List.of(
                historyRecord("OBJECT", "meta.gl_balance", "APPROVED", OffsetDateTime.parse("2026-06-20T00:00:00Z")),
                historyRecord("OBJECT", "meta.gl_balance", "REGISTERED", OffsetDateTime.parse("2026-06-18T00:00:00Z"))
        );
        GovernanceHistoryReadServiceImpl service = new GovernanceHistoryReadServiceImpl(dao);

        List<GovernanceHistoryEventDto> results =
                service.findHistory("client-a", "OBJECT", "meta.gl_balance", null);

        assertEquals(1, dao.calls.size());
        assertEquals(2, results.size());
        assertEquals("APPROVED", results.get(0).change_type_cd());
        assertEquals(OffsetDateTime.parse("2026-06-20T00:00:00Z"), results.get(0).event_ts());
        assertEquals("REGISTERED", results.get(1).change_type_cd());
        assertEquals(OffsetDateTime.parse("2026-06-18T00:00:00Z"), results.get(1).event_ts());
    }

    @Test
    void resolvesSupportedEntityTypesWithoutChangingIdentity() {
        GovernanceHistoryReadServiceImpl service = new GovernanceHistoryReadServiceImpl(
                new RecordingGovernanceHistoryReadDao() {
                    @Override
                    public List<GovernanceHistoryEventRecord> findEvents(String clientId,
                                                                         String entityTypeCode,
                                                                         String entityRef,
                                                                         String changeTypeCode) {
                        calls.add(new Call(clientId, entityTypeCode, entityRef, changeTypeCode));
                        return List.of(historyRecord(entityTypeCode, entityRef, "REGISTERED",
                                OffsetDateTime.parse("2026-06-18T00:00:00Z")));
                    }
                }
        );

        assertResolvedEntityType(service, "OBJECT", "meta.gl_balance");
        assertResolvedEntityType(service, "RELATIONSHIP", "gl_to_ledger");
        assertResolvedEntityType(service, "ATTRIBUTE_PAIRING", "customer_name_to_id");
        assertResolvedEntityType(service, "FILTER_LOOKUP", "ledger_scope");
        assertResolvedEntityType(service, "DQ_RULE", "gl_balance_not_null");
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

    private static void assertResolvedEntityType(GovernanceHistoryReadServiceImpl service,
                                                 String entityTypeCode,
                                                 String entityRef) {
        List<GovernanceHistoryEventDto> results =
                service.findHistory("client-a", entityTypeCode, entityRef, null);

        assertEquals(1, results.size());
        assertEquals(entityTypeCode, results.get(0).entity_type_cd());
        assertEquals(entityRef, results.get(0).entity_ref());
    }

    private static GovernanceHistoryEventRecord historyRecord(String entityTypeCode,
                                                              String entityRef,
                                                              String changeTypeCode,
                                                              OffsetDateTime eventTs) {
        return new GovernanceHistoryEventRecord(
                501L,
                "client-a",
                entityTypeCode,
                entityRef,
                changeTypeCode,
                "Object registered",
                "producer",
                eventTs,
                "{\"status\":\"DRAFT\"}",
                "{\"status\":\"ACTIVE\"}"
        );
    }

    private record Call(String clientId, String entityTypeCode, String entityRef, String changeTypeCode) {
    }

    private static class RecordingGovernanceHistoryReadDao implements GovernanceHistoryReadDao {

        protected final List<Call> calls = new ArrayList<>();
        protected List<GovernanceHistoryEventRecord> baseEvents = List.of();
        protected List<GovernanceHistoryEventRecord> filteredEvents = List.of();

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
