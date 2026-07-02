package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.LogicalPhysicalResolutionDao;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.model.LogicalPhysicalResolutionRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicalPhysicalResolutionServiceImplTest {

    @Test
    void returnsEmptyListWhenNoLogicalAttributesProvided() {
        RecordingLogicalPhysicalResolutionDao dao = new RecordingLogicalPhysicalResolutionDao();
        LogicalPhysicalResolutionServiceImpl service = new LogicalPhysicalResolutionServiceImpl(dao);

        List<LogicalPhysicalResolutionDto> results = service.resolveAttributes("client-a", "meta", "ledger", List.of());

        assertTrue(results.isEmpty());
        assertEquals(0, dao.attributeRequests.size());
    }

    @Test
    void mapsAttributeResolutionRowsToDtos() {
        RecordingLogicalPhysicalResolutionDao dao = new RecordingLogicalPhysicalResolutionDao();
        dao.attributeResults = List.of(record("ledger_id", "Ledger Identifier Override", null));
        LogicalPhysicalResolutionServiceImpl service = new LogicalPhysicalResolutionServiceImpl(dao);

        List<LogicalPhysicalResolutionDto> results = service.resolveAttributes("client-a", "meta", "ledger", List.of("ledger_id"));

        assertEquals(1, results.size());
        assertEquals("ledger_id", results.get(0).logical_attribute_cd());
        assertEquals("Ledger Identifier Override", results.get(0).effective_logical_attribute_nm());
        assertEquals("ledger_id", results.get(0).physical_attribute_nm());
        assertEquals("POSTGRES", results.get(0).engine_cd());
        assertEquals(List.of("client-a|meta|ledger|[ledger_id]"), dao.attributeRequests);
    }

    @Test
    void mapsOutboundResolutionRowsToDtos() {
        RecordingLogicalPhysicalResolutionDao dao = new RecordingLogicalPhysicalResolutionDao();
        dao.outboundResults = List.of(record("ledger_id", "Ledger Identifier Override", 77L));
        LogicalPhysicalResolutionServiceImpl service = new LogicalPhysicalResolutionServiceImpl(dao);

        List<LogicalPhysicalResolutionDto> results = service.resolveOutboundGrain("client-a", 77L);

        assertEquals(1, results.size());
        assertEquals(77L, results.get(0).outbound_id());
        assertEquals("OB-77", results.get(0).outbound_cd());
        assertEquals(1, results.get(0).grain_level_nbr());
        assertEquals("ledger_id", results.get(0).logical_attribute_cd());
        assertEquals("Ledger Identifier Override", results.get(0).effective_logical_attribute_nm());
        assertEquals("POSTGRES", results.get(0).engine_cd());
        assertEquals(List.of("client-a|77"), dao.outboundRequests);
    }

    private static LogicalPhysicalResolutionRecord record(String logicalAttributeCode,
                                                          String effectiveAttributeName,
                                                          Long outboundId) {
        return new LogicalPhysicalResolutionRecord(
                outboundId,
                outboundId == null ? null : "OB-77",
                outboundId == null ? null : 1,
                "client-a",
                "meta",
                "ledger",
                logicalAttributeCode,
                effectiveAttributeName,
                "ledger_id",
                "ledger_source",
                "POSTGRES",
                "NUMBER"
        );
    }

    private static final class RecordingLogicalPhysicalResolutionDao implements LogicalPhysicalResolutionDao {

        private final List<String> attributeRequests = new ArrayList<>();
        private final List<String> outboundRequests = new ArrayList<>();
        private List<LogicalPhysicalResolutionRecord> attributeResults = List.of();
        private List<LogicalPhysicalResolutionRecord> outboundResults = List.of();

        @Override
        public List<LogicalPhysicalResolutionRecord> findByAttributes(String clientId,
                                                                      String schemaCode,
                                                                      String objectCode,
                                                                      List<String> logicalAttributeCodes) {
            attributeRequests.add(clientId + "|" + schemaCode + "|" + objectCode + "|" + logicalAttributeCodes);
            return attributeResults;
        }

        @Override
        public List<LogicalPhysicalResolutionRecord> findByOutboundGrain(String clientId, Long outboundId) {
            outboundRequests.add(clientId + "|" + outboundId);
            return outboundResults;
        }
    }
}
