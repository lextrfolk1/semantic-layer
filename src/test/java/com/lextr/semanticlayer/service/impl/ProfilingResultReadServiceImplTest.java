package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dao.ProfilingResultReadDao;
import com.lextr.semanticlayer.dto.ProfilingResultDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.model.ProfilingResultRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfilingResultReadServiceImplTest {

    @Test
    void appliesClientScopingAndFiltersWhenObjectExists() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao(
                Optional.of(objectRecord(objectId)),
                List.of(attributeRecord(objectId))
        );
        RecordingProfilingResultReadDao profilingDao = new RecordingProfilingResultReadDao(List.of(profilingRecord()));
        ProfilingResultReadServiceImpl service = new ProfilingResultReadServiceImpl(objectDao, profilingDao);

        List<ProfilingResultDto> results = service.findMetrics("client-a", objectId, "COMPLETED");

        assertEquals(1, results.size());
        assertEquals("Ledger Identifier Override", results.get(0).attribute_name());
        assertEquals("SOURCE", results.get(0).inferred_role());
        assertEquals(Integer.valueOf(0), results.get(0).null_percentage());
        assertEquals(Integer.valueOf(100), results.get(0).distinct_percentage());
        assertEquals("COMPLETED", results.get(0).profiling_status());
        assertEquals("client-a", objectDao.lastClientId);
        assertEquals(objectId, objectDao.lastObjectId);
        assertEquals("client-a", objectDao.lastAttributesClientId);
        assertEquals(objectId, objectDao.lastAttributesObjectId);
        assertEquals("client-a", profilingDao.lastClientId);
        assertEquals("meta", profilingDao.lastSchemaCode);
        assertEquals("GL_BALANCE", profilingDao.lastObjectCode);
        assertEquals("COMPLETED", profilingDao.lastProfilingStatusCode);
    }

    @Test
    void fallsBackToLogicalAttributeNameWhenOverrideMissing() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao(
                Optional.of(objectRecord(objectId)),
                List.of()
        );
        RecordingProfilingResultReadDao profilingDao = new RecordingProfilingResultReadDao(List.of(profilingRecord()));
        ProfilingResultReadServiceImpl service = new ProfilingResultReadServiceImpl(objectDao, profilingDao);

        List<ProfilingResultDto> results = service.findMetrics("client-a", objectId, null);

        assertEquals(1, results.size());
        assertEquals("ledger_id", results.get(0).attribute_name());
    }

    @Test
    void returns404WhenObjectMissingWithinClientScope() {
        ProfilingResultReadServiceImpl service = new ProfilingResultReadServiceImpl(
                new RecordingObjectExposureReadDao(Optional.empty(), List.of()),
                new RecordingProfilingResultReadDao(List.of())
        );

        assertThrows(
                RegistryResourceNotFoundException.class,
                () -> service.findMetrics("client-a", UUID.fromString("00000000-0000-0000-0000-000000000999"), null)
        );
    }

    private static ObjectExposureRecord objectRecord(UUID objectId) {
        return new ObjectExposureRecord(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "GL Balance Override",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "CONFIDENTIAL",
                true,
                true,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
    }

    private static AttributeExposureRecord attributeRecord(UUID objectId) {
        return new AttributeExposureRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                objectId,
                "client-a",
                "ledger_id",
                "ledger_id",
                "Ledger Identifier Override",
                "NUMBER",
                "finance",
                "core",
                "GLOBAL",
                "CONFIDENTIAL",
                true,
                false,
                null,
                false,
                false,
                "LOW",
                false,
                false,
                false,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
    }

    private static ProfilingResultRecord profilingRecord() {
        return new ProfilingResultRecord(
                101L,
                "client-a",
                "meta",
                "GL_BALANCE",
                "ledger_id",
                "SOURCE",
                0,
                100,
                "COMPLETED",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "profiler",
                OffsetDateTime.parse("2026-06-18T11:15:30Z"),
                "profiler"
        );
    }

    private static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private final Optional<ObjectExposureRecord> object;
        private final List<AttributeExposureRecord> attributes;
        private String lastClientId;
        private UUID lastObjectId;
        private String lastAttributesClientId;
        private UUID lastAttributesObjectId;

        private RecordingObjectExposureReadDao(Optional<ObjectExposureRecord> object, List<AttributeExposureRecord> attributes) {
            this.object = object;
            this.attributes = attributes;
        }

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            lastClientId = clientId;
            lastObjectId = objectId;
            return object;
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return object;
        }

        @Override
        public List<com.lextr.semanticlayer.model.AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            lastAttributesClientId = clientId;
            lastAttributesObjectId = objectId;
            return attributes;
        }

        @Override
        public List<com.lextr.semanticlayer.model.AttributeAccessGrantRecord> findAttributeAccessGrants(String clientId, String schemaCode, String objectCode, String attributeCode) {
            return List.of();
        }

        @Override
        public void insertAccessAudit(com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest request) {
        }
    }

    private static final class RecordingProfilingResultReadDao implements ProfilingResultReadDao {

        private final List<ProfilingResultRecord> records;
        private String lastClientId;
        private String lastSchemaCode;
        private String lastObjectCode;
        private String lastProfilingStatusCode;

        private RecordingProfilingResultReadDao(List<ProfilingResultRecord> records) {
            this.records = records;
        }

        @Override
        public List<ProfilingResultRecord> findMetrics(String clientId, String schemaCode, String objectCode, String profilingStatusCode) {
            lastClientId = clientId;
            lastSchemaCode = schemaCode;
            lastObjectCode = objectCode;
            lastProfilingStatusCode = profilingStatusCode;
            return records;
        }
    }
}
