package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectExposureReadServiceImplTest {

    @Test
    void mapsObjectRowsToSummaryDtosUsingEffectiveOverrideWhenPresent() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        ObjectExposureRecord record = new ObjectExposureRecord(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "GL Balance Override",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
        ObjectExposureReadServiceImpl service = new ObjectExposureReadServiceImpl(new FixedObjectExposureReadDao(List.of(record), List.of()));

        List<ObjectExposureSummaryDto> results = service.findObjects("client-a", "meta", "ACTIVE");

        assertEquals(1, results.size());
        assertEquals(objectId, results.get(0).object_id());
        assertEquals("GL Balance Override", results.get(0).object_nm());
        assertEquals("ACTIVE", results.get(0).lifecycle_status_cd());
    }

    @Test
    void mapsObjectDetailWithAttributesUsingEffectiveOverridesWhenPresent() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        ObjectExposureRecord object = new ObjectExposureRecord(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "GL Balance Override",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                null,
                null
        );
        AttributeExposureRecord attribute = new AttributeExposureRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                objectId,
                "client-a",
                "AMOUNT",
                "Amount",
                "Amount Override",
                "DECIMAL",
                "MDRM12345678",
                "MDRM",
                "US",
                true,
                false,
                false,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                null,
                null
        );
        ObjectExposureReadServiceImpl service = new ObjectExposureReadServiceImpl(new FixedObjectExposureReadDao(List.of(object), List.of(attribute)));

        ObjectExposureDetailDto result = service.findObject("client-a", objectId);

        assertEquals("GL Balance Override", result.object_nm());
        assertEquals(1, result.attributes().size());
        assertEquals("Amount Override", result.attributes().get(0).attribute_nm());
        assertEquals("MDRM12345678", result.attributes().get(0).taxonomy_cd());
        assertEquals(true, result.attributes().get(0).pk_flg());
        assertEquals(false, result.attributes().get(0).fk_flg());
        assertEquals(false, result.attributes().get(0).nullable_flg());
    }

    @Test
    void fallsBackToBaseNamesWhenNoEffectiveOverrideExists() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        ObjectExposureRecord object = new ObjectExposureRecord(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                " ",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                null,
                null
        );
        AttributeExposureRecord attribute = new AttributeExposureRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                objectId,
                "client-a",
                "AMOUNT",
                "Amount",
                "",
                "DECIMAL",
                "MDRM12345678",
                "MDRM",
                "US",
                false,
                false,
                true,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                null,
                null
        );
        ObjectExposureReadServiceImpl service = new ObjectExposureReadServiceImpl(new FixedObjectExposureReadDao(List.of(object), List.of(attribute)));

        ObjectExposureDetailDto result = service.findObject("client-a", objectId);

        assertEquals("GL Balance", result.object_nm());
        assertEquals("Amount", result.attributes().get(0).attribute_nm());
        assertEquals(true, result.attributes().get(0).nullable_flg());
    }

    @Test
    void returns404WhenObjectMissing() {
        ObjectExposureReadServiceImpl service = new ObjectExposureReadServiceImpl(new FixedObjectExposureReadDao(List.of(), List.of()));

        assertThrows(RegistryResourceNotFoundException.class,
                () -> service.findObject("client-a", UUID.fromString("00000000-0000-0000-0000-000000000999")));
    }

    private static final class FixedObjectExposureReadDao implements ObjectExposureReadDao {

        private final List<ObjectExposureRecord> objects;
        private final List<AttributeExposureRecord> attributes;

        private FixedObjectExposureReadDao(List<ObjectExposureRecord> objects, List<AttributeExposureRecord> attributes) {
            this.objects = objects;
            this.attributes = attributes;
        }

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            return objects;
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            return objects.stream().filter(record -> objectId.equals(record.object_id())).findFirst();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return objects.stream()
                    .filter(record -> schemaCode.equals(record.schema_cd()))
                    .filter(record -> objectCode.equals(record.object_cd()))
                    .findFirst();
        }

        @Override
        public List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            return attributes.stream().filter(record -> objectId.equals(record.object_id())).toList();
        }
    }
}
