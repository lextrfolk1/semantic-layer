package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.ObjectExposureAccessPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.AttributeAccessGrantRecord;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.ObjectExposurePolicyClient;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectExposureReadServiceImplTest {

    @Test
    void mapsObjectRowsToSummaryDtosUsingEffectiveOverrideWhenPresentAndWritesAudit() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        ObjectExposureRecord record = objectRecord(objectId, "GL Balance Override");
        RecordingObjectExposureReadDao dao = new RecordingObjectExposureReadDao(List.of(record), List.of());
        RecordingObjectExposurePolicyClient policyClient = new RecordingObjectExposurePolicyClient();
        ObjectExposureReadServiceImpl service = new ObjectExposureReadServiceImpl(dao, policyClient);

        List<ObjectExposureSummaryDto> results = service.findObjects("client-a", "analyst-1", "FINANCE", "REPORTING", "meta", "ACTIVE");

        assertEquals(1, results.size());
        assertEquals(objectId, results.get(0).object_id());
        assertEquals("GL Balance Override", results.get(0).object_nm());
        assertEquals("ACTIVE", results.get(0).lifecycle_status_cd());
        assertEquals(1, policyClient.accessRequests.size());
        assertEquals(1, policyClient.classificationRequests.size());
        assertEquals(1, dao.audits.size());
        assertTrue(dao.audits.get(0).change_reason_txt().contains("masked=0"));
    }

    @Test
    void masksAndWithholdsUnentitledFieldsInObjectDetail() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        AttributeExposureRecord maskedAttribute = attributeRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                objectId,
                "AMOUNT",
                "Amount Override"
        );
        AttributeExposureRecord withheldAttribute = attributeRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000103"),
                objectId,
                "ACCOUNT_NO",
                "Account Number"
        );
        RecordingObjectExposureReadDao dao = new RecordingObjectExposureReadDao(
                List.of(objectRecord(objectId, "GL Balance Override")),
                List.of(maskedAttribute, withheldAttribute)
        );
        dao.attributeAccessGrants = List.of(grantRecord("READ", "ACTIVE"));

        RecordingObjectExposurePolicyClient policyClient = new RecordingObjectExposurePolicyClient();
        policyClient.maskAttribute("AMOUNT", List.of("attribute_nm", "taxonomy_cd"), "REDACTED");
        policyClient.withholdAttribute("ACCOUNT_NO");

        ObjectExposureReadServiceImpl service = new ObjectExposureReadServiceImpl(dao, policyClient);

        ObjectExposureDetailDto result = service.findObject("client-a", "analyst-1", "FINANCE", "REPORTING", objectId);

        assertEquals("GL Balance Override", result.object_nm());
        assertEquals(1, result.attributes().size());
        assertEquals("AMOUNT", result.attributes().get(0).attribute_cd());
        assertEquals("REDACTED", result.attributes().get(0).attribute_nm());
        assertEquals("REDACTED", result.attributes().get(0).taxonomy_cd());
        assertEquals(2, policyClient.attributeAccessRequests().size());
        assertEquals(2, policyClient.attributeClassificationRequests().size());
        assertEquals(2, dao.lastGrantLookups.size());
        assertEquals(1, dao.audits.size());
        assertTrue(dao.audits.get(0).change_reason_txt().contains("masked=1"));
        assertTrue(dao.audits.get(0).change_reason_txt().contains("withheld=1"));
    }

    @Test
    void deniesCrossTenantObjectDetailWhenPolicyRejectsAccess() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao dao = new RecordingObjectExposureReadDao(
                List.of(objectRecord(objectId, "GL Balance Override")),
                List.of()
        );
        RecordingObjectExposurePolicyClient policyClient = new RecordingObjectExposurePolicyClient();
        policyClient.denyObject("POL-AC-001", "Cross-tenant access denied");

        ObjectExposureReadServiceImpl service = new ObjectExposureReadServiceImpl(dao, policyClient);

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.findObject("client-b", "analyst-2", "FINANCE", "REPORTING", objectId)
        );

        assertEquals("POL-AC-001", exception.code());
        assertEquals("Cross-tenant access denied", exception.getMessage());
        assertEquals(1, dao.audits.size());
        assertTrue(dao.audits.get(0).change_reason_txt().contains("denied"));
    }

    @Test
    void returns404WhenObjectMissing() {
        ObjectExposureReadServiceImpl service = new ObjectExposureReadServiceImpl(
                new RecordingObjectExposureReadDao(List.of(), List.of()),
                new RecordingObjectExposurePolicyClient()
        );

        assertThrows(RegistryResourceNotFoundException.class,
                () -> service.findObject("client-a", "analyst-1", "FINANCE", "REPORTING",
                        UUID.fromString("00000000-0000-0000-0000-000000000999")));
    }

    private static ObjectExposureRecord objectRecord(UUID objectId, String effectiveObjectName) {
        return new ObjectExposureRecord(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                effectiveObjectName,
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

    private static AttributeExposureRecord attributeRecord(UUID attributeId, UUID objectId, String attributeCode, String effectiveName) {
        return new AttributeExposureRecord(
                attributeId,
                objectId,
                "client-a",
                attributeCode,
                attributeCode.equals("AMOUNT") ? "Amount" : "Account Number",
                effectiveName,
                "DECIMAL",
                "MDRM12345678",
                "MDRM",
                "US",
                "RESTRICTED",
                true,
                true,
                "MASK_FULL",
                false,
                false,
                "RESTRICTED",
                true,
                false,
                false,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                null,
                null
        );
    }

    private static AttributeAccessGrantRecord grantRecord(String scope, String status) {
        return new AttributeAccessGrantRecord(
                10L,
                "client-a",
                "meta",
                "GL_BALANCE",
                "AMOUNT",
                "FINANCE",
                "REPORTING",
                scope,
                status,
                "approver",
                OffsetDateTime.parse("2026-06-18T12:00:00Z"),
                OffsetDateTime.parse("2026-06-18T11:00:00Z"),
                "approver",
                null,
                null
        );
    }

    private static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private final List<ObjectExposureRecord> objects;
        private final List<AttributeExposureRecord> attributes;
        private List<AttributeAccessGrantRecord> attributeAccessGrants = List.of();
        private final List<String> lastGrantLookups = new ArrayList<>();
        private final List<ObjectExposureAccessAuditWriteRequest> audits = new ArrayList<>();

        private RecordingObjectExposureReadDao(List<ObjectExposureRecord> objects, List<AttributeExposureRecord> attributes) {
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

        @Override
        public List<AttributeAccessGrantRecord> findAttributeAccessGrants(String clientId,
                                                                          String schemaCode,
                                                                          String objectCode,
                                                                          String attributeCode) {
            lastGrantLookups.add(schemaCode + "." + objectCode + "." + attributeCode);
            return attributeAccessGrants;
        }

        @Override
        public void insertAccessAudit(ObjectExposureAccessAuditWriteRequest request) {
            audits.add(request);
        }
    }

    private static final class RecordingObjectExposurePolicyClient implements ObjectExposurePolicyClient {

        private final List<ObjectExposureAccessPolicyRequestDto> accessRequests = new ArrayList<>();
        private final List<ObjectExposureClassificationPolicyRequestDto> classificationRequests = new ArrayList<>();
        private ObjectExposurePolicyDecisionDto objectDecision = new ObjectExposurePolicyDecisionDto(true, null, null);
        private String maskedAttributeCode;
        private List<String> maskedFields = List.of();
        private String maskValue;
        private String withheldAttributeCode;

        void denyObject(String code, String message) {
            objectDecision = new ObjectExposurePolicyDecisionDto(false, code, message);
        }

        void maskAttribute(String attributeCode, List<String> fields, String value) {
            maskedAttributeCode = attributeCode;
            maskedFields = fields;
            maskValue = value;
        }

        void withholdAttribute(String attributeCode) {
            withheldAttributeCode = attributeCode;
        }

        List<ObjectExposureAccessPolicyRequestDto> attributeAccessRequests() {
            return accessRequests.stream().filter(request -> request.attribute_cd() != null).toList();
        }

        List<ObjectExposureClassificationPolicyRequestDto> attributeClassificationRequests() {
            return classificationRequests.stream().filter(request -> request.attribute_cd() != null).toList();
        }

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(ObjectExposureAccessPolicyRequestDto request) {
            accessRequests.add(request);
            if (request.attribute_cd() == null) {
                return objectDecision;
            }
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }

        @Override
        public ObjectExposureClassificationPolicyDecisionDto evaluateClassification(ObjectExposureClassificationPolicyRequestDto request) {
            classificationRequests.add(request);
            if (request.attribute_cd() == null) {
                return new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null);
            }
            if (request.attribute_cd().equals(withheldAttributeCode)) {
                return new ObjectExposureClassificationPolicyDecisionDto(true, false, true, null, List.of(), "POL-DC-001", "Attribute withheld");
            }
            if (request.attribute_cd().equals(maskedAttributeCode)) {
                return new ObjectExposureClassificationPolicyDecisionDto(true, true, false, maskValue, maskedFields, null, null);
            }
            return new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null);
        }
    }
}
