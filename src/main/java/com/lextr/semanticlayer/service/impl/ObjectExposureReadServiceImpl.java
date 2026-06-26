package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.AttributeExposureDto;
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
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ObjectExposureReadServiceImpl implements ObjectExposureReadService {

    private static final String ACCESS_POLICY_CD = "POL-AC-001";
    private static final String CLASSIFICATION_POLICY_CD = "POL-DC-001";
    private static final String REQUEST_TYPE_LIST = "LIST";
    private static final String REQUEST_TYPE_DETAIL = "DETAIL";
    private static final String ENTITY_TYPE_CD = "OBJECT_EXPOSURE";
    private static final String CHANGE_TYPE_CD = "READ";
    private static final String DEFAULT_AUDIT_ACTOR = "semantic-layer";
    private static final String FIELD_OBJECT_NAME = "object_nm";
    private static final String FIELD_ATTRIBUTE_NAME = "attribute_nm";
    private static final String FIELD_TAXONOMY_CD = "taxonomy_cd";
    private static final String FIELD_TAXONOMY_SOURCE_CD = "taxonomy_source_cd";
    private static final String FIELD_TAXONOMY_JURISDICTION_CD = "taxonomy_jurisdiction_cd";

    private final ObjectExposureReadDao objectExposureReadDao;
    private final ObjectExposurePolicyClient objectExposurePolicyClient;

    @Autowired
    public ObjectExposureReadServiceImpl(ObjectExposureReadDao objectExposureReadDao,
                                         ObjectProvider<ObjectExposurePolicyClient> objectExposurePolicyClientProvider) {
        this(
                objectExposureReadDao,
                objectExposurePolicyClientProvider.getIfAvailable(DefaultObjectExposurePolicyClient::new)
        );
    }

    public ObjectExposureReadServiceImpl(ObjectExposureReadDao objectExposureReadDao,
                                         ObjectExposurePolicyClient objectExposurePolicyClient) {
        this.objectExposureReadDao = objectExposureReadDao;
        this.objectExposurePolicyClient = objectExposurePolicyClient;
    }

    @Override
    public List<ObjectExposureSummaryDto> findObjects(String clientId,
                                                      String actorId,
                                                      String roleCode,
                                                      String purposeCode,
                                                      String schemaCode,
                                                      String lifecycleStatusCode) {
        List<ObjectExposureRecord> objects = objectExposureReadDao.findObjects(clientId, schemaCode, lifecycleStatusCode);
        List<ObjectExposureSummaryDto> visibleObjects = new ArrayList<>();
        int maskedCount = 0;
        int withheldCount = 0;

        for (ObjectExposureRecord object : objects) {
            ObjectExposurePolicyDecisionDto accessDecision = objectExposurePolicyClient.evaluateAccess(
                    accessRequest(REQUEST_TYPE_LIST, clientId, actorId, roleCode, purposeCode, object, null, List.of())
            );
            ObjectExposureClassificationPolicyDecisionDto classificationDecision = objectExposurePolicyClient.evaluateClassification(
                    classificationRequest(REQUEST_TYPE_LIST, clientId, actorId, roleCode, purposeCode, object, null)
            );

            if (!accessDecision.allowed() || !classificationDecision.allowed() || classificationDecision.withheld()) {
                withheldCount++;
                continue;
            }

            ObjectExposureSummaryDto dto = applyObjectMask(toSummaryDto(object), classificationDecision);
            if (classificationDecision.masked()) {
                maskedCount++;
            }
            visibleObjects.add(dto);
        }

        writeAudit(
                actorId,
                listEntityRef(clientId, schemaCode, lifecycleStatusCode),
                "Object exposure list returned " + visibleObjects.size()
                        + " objects; masked=" + maskedCount
                        + "; withheld=" + withheldCount
        );
        return visibleObjects;
    }

    @Override
    public ObjectExposureDetailDto findObject(String clientId,
                                              String actorId,
                                              String roleCode,
                                              String purposeCode,
                                              UUID objectId) {
        ObjectExposureRecord object = objectExposureReadDao.findObject(clientId, objectId)
                .orElseThrow(() -> new RegistryResourceNotFoundException("object", objectId.toString()));

        ObjectExposurePolicyDecisionDto objectAccessDecision = objectExposurePolicyClient.evaluateAccess(
                accessRequest(REQUEST_TYPE_DETAIL, clientId, actorId, roleCode, purposeCode, object, null, List.of())
        );
        ObjectExposureClassificationPolicyDecisionDto objectClassificationDecision = objectExposurePolicyClient.evaluateClassification(
                classificationRequest(REQUEST_TYPE_DETAIL, clientId, actorId, roleCode, purposeCode, object, null)
        );

        if (!objectAccessDecision.allowed()) {
            writeAudit(actorId, objectId.toString(), "Object exposure denied by " + policyCode(objectAccessDecision, ACCESS_POLICY_CD));
            throw new PolicyViolationException(policyCode(objectAccessDecision, ACCESS_POLICY_CD), policyMessage(objectAccessDecision, "Access denied"));
        }
        if (!objectClassificationDecision.allowed() || objectClassificationDecision.withheld()) {
            writeAudit(
                    actorId,
                    objectId.toString(),
                    "Object exposure withheld by " + policyCode(objectClassificationDecision, CLASSIFICATION_POLICY_CD)
            );
            throw new PolicyViolationException(
                    policyCode(objectClassificationDecision, CLASSIFICATION_POLICY_CD),
                    policyMessage(objectClassificationDecision, "Object is not available for exposure")
            );
        }

        List<AttributeExposureDto> visibleAttributes = new ArrayList<>();
        int maskedCount = objectClassificationDecision.masked() ? 1 : 0;
        int withheldCount = 0;

        for (AttributeExposureRecord attribute : objectExposureReadDao.findAttributes(clientId, objectId)) {
            List<AttributeAccessGrantRecord> grants = objectExposureReadDao.findAttributeAccessGrants(
                    clientId,
                    object.schema_cd(),
                    object.object_cd(),
                    attribute.attribute_cd()
            );
            ObjectExposurePolicyDecisionDto accessDecision = objectExposurePolicyClient.evaluateAccess(
                    accessRequest(REQUEST_TYPE_DETAIL, clientId, actorId, roleCode, purposeCode, object, attribute, grants)
            );
            ObjectExposureClassificationPolicyDecisionDto classificationDecision = objectExposurePolicyClient.evaluateClassification(
                    classificationRequest(REQUEST_TYPE_DETAIL, clientId, actorId, roleCode, purposeCode, object, attribute)
            );

            if (!accessDecision.allowed() || !classificationDecision.allowed() || classificationDecision.withheld()) {
                withheldCount++;
                continue;
            }

            AttributeExposureDto attributeDto = applyAttributeMask(toAttributeDto(attribute), classificationDecision);
            if (classificationDecision.masked()) {
                maskedCount++;
            }
            visibleAttributes.add(attributeDto);
        }

        ObjectExposureDetailDto detail = applyObjectMask(toDetailDto(object, visibleAttributes), objectClassificationDecision);
        writeAudit(
                actorId,
                objectId.toString(),
                "Object exposure detail returned " + visibleAttributes.size()
                        + " attributes; masked=" + maskedCount
                        + "; withheld=" + withheldCount
        );
        return detail;
    }

    private ObjectExposureAccessPolicyRequestDto accessRequest(String requestType,
                                                               String clientId,
                                                               String actorId,
                                                               String roleCode,
                                                               String purposeCode,
                                                               ObjectExposureRecord object,
                                                               AttributeExposureRecord attribute,
                                                               List<AttributeAccessGrantRecord> grants) {
        return new ObjectExposureAccessPolicyRequestDto(
                ACCESS_POLICY_CD,
                requestType,
                clientId,
                actorId,
                roleCode,
                purposeCode,
                object.object_id(),
                object.schema_cd(),
                object.object_cd(),
                object.object_type_cd(),
                object.client_id(),
                object.data_classification_cd(),
                attribute == null ? null : attribute.attribute_cd(),
                grants.stream().map(AttributeAccessGrantRecord::grant_scope_cd).toList(),
                grants.stream().map(AttributeAccessGrantRecord::grant_status_cd).toList()
        );
    }

    private ObjectExposureClassificationPolicyRequestDto classificationRequest(String requestType,
                                                                               String clientId,
                                                                               String actorId,
                                                                               String roleCode,
                                                                               String purposeCode,
                                                                               ObjectExposureRecord object,
                                                                               AttributeExposureRecord attribute) {
        return new ObjectExposureClassificationPolicyRequestDto(
                CLASSIFICATION_POLICY_CD,
                requestType,
                clientId,
                actorId,
                roleCode,
                purposeCode,
                object.object_id(),
                object.schema_cd(),
                object.object_cd(),
                object.data_classification_cd(),
                object.pii_flg(),
                object.confidential_flg(),
                attribute == null ? null : attribute.attribute_cd(),
                attribute == null ? null : attribute.data_classification_cd(),
                attribute != null && attribute.pii_flg(),
                attribute != null && attribute.confidential_flg(),
                attribute == null ? null : attribute.masking_policy_cd(),
                attribute != null && attribute.mnpi_flg(),
                attribute != null && attribute.csi_flg(),
                attribute == null ? null : attribute.ai_exposure_cd(),
                attribute == null ? null : attribute.taxonomy_jurisdiction_cd()
        );
    }

    private ObjectExposureSummaryDto toSummaryDto(ObjectExposureRecord record) {
        return new ObjectExposureSummaryDto(
                record.object_id(),
                record.object_cd(),
                effectiveValue(record.effective_object_nm(), record.object_nm()),
                record.object_type_cd(),
                record.schema_cd(),
                record.connection_id(),
                record.lifecycle_status_cd(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private ObjectExposureDetailDto toDetailDto(ObjectExposureRecord record, List<AttributeExposureDto> attributes) {
        return new ObjectExposureDetailDto(
                record.object_id(),
                record.object_cd(),
                effectiveValue(record.effective_object_nm(), record.object_nm()),
                record.object_type_cd(),
                record.schema_cd(),
                record.connection_id(),
                record.lifecycle_status_cd(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by(),
                attributes
        );
    }

    private AttributeExposureDto toAttributeDto(AttributeExposureRecord record) {
        return new AttributeExposureDto(
                record.attribute_id(),
                record.attribute_cd(),
                effectiveValue(record.effective_attribute_nm(), record.attribute_nm()),
                record.data_type_cd(),
                record.taxonomy_cd(),
                record.taxonomy_source_cd(),
                record.taxonomy_jurisdiction_cd(),
                record.pk_flg(),
                record.fk_flg(),
                record.nullable_flg(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private ObjectExposureSummaryDto applyObjectMask(ObjectExposureSummaryDto dto,
                                                     ObjectExposureClassificationPolicyDecisionDto decision) {
        if (!decision.masked()) {
            return dto;
        }
        return new ObjectExposureSummaryDto(
                dto.object_id(),
                dto.object_cd(),
                maskedValue(decision, FIELD_OBJECT_NAME, dto.object_nm()),
                dto.object_type_cd(),
                dto.schema_cd(),
                dto.connection_id(),
                dto.lifecycle_status_cd(),
                dto.created_ts(),
                dto.created_by(),
                dto.updated_ts(),
                dto.updated_by()
        );
    }

    private ObjectExposureDetailDto applyObjectMask(ObjectExposureDetailDto dto,
                                                    ObjectExposureClassificationPolicyDecisionDto decision) {
        if (!decision.masked()) {
            return dto;
        }
        return new ObjectExposureDetailDto(
                dto.object_id(),
                dto.object_cd(),
                maskedValue(decision, FIELD_OBJECT_NAME, dto.object_nm()),
                dto.object_type_cd(),
                dto.schema_cd(),
                dto.connection_id(),
                dto.lifecycle_status_cd(),
                dto.created_ts(),
                dto.created_by(),
                dto.updated_ts(),
                dto.updated_by(),
                dto.attributes()
        );
    }

    private AttributeExposureDto applyAttributeMask(AttributeExposureDto dto,
                                                    ObjectExposureClassificationPolicyDecisionDto decision) {
        if (!decision.masked()) {
            return dto;
        }
        return new AttributeExposureDto(
                dto.attribute_id(),
                dto.attribute_cd(),
                maskedValue(decision, FIELD_ATTRIBUTE_NAME, dto.attribute_nm()),
                dto.data_type_cd(),
                maskedValue(decision, FIELD_TAXONOMY_CD, dto.taxonomy_cd()),
                maskedValue(decision, FIELD_TAXONOMY_SOURCE_CD, dto.taxonomy_source_cd()),
                maskedValue(decision, FIELD_TAXONOMY_JURISDICTION_CD, dto.taxonomy_jurisdiction_cd()),
                dto.pk_flg(),
                dto.fk_flg(),
                dto.nullable_flg(),
                dto.created_ts(),
                dto.created_by(),
                dto.updated_ts(),
                dto.updated_by()
        );
    }

    private String maskedValue(ObjectExposureClassificationPolicyDecisionDto decision, String fieldName, String currentValue) {
        if (!shouldMask(decision, fieldName)) {
            return currentValue;
        }
        return decision.mask_value_txt() == null || decision.mask_value_txt().isBlank()
                ? "MASKED"
                : decision.mask_value_txt();
    }

    private boolean shouldMask(ObjectExposureClassificationPolicyDecisionDto decision, String fieldName) {
        return decision.masked_fields() != null && decision.masked_fields().contains(fieldName);
    }

    private String effectiveValue(String effectiveValue, String baseValue) {
        return effectiveValue == null || effectiveValue.isBlank() ? baseValue : effectiveValue;
    }

    private void writeAudit(String actorId, String entityRef, String reason) {
        objectExposureReadDao.insertAccessAudit(new ObjectExposureAccessAuditWriteRequest(
                ENTITY_TYPE_CD,
                entityRef,
                CHANGE_TYPE_CD,
                auditActor(actorId),
                OffsetDateTime.now(ZoneOffset.UTC),
                reason
        ));
    }

    private String auditActor(String actorId) {
        return actorId == null || actorId.isBlank() ? DEFAULT_AUDIT_ACTOR : actorId;
    }

    private String listEntityRef(String clientId, String schemaCode, String lifecycleStatusCode) {
        return clientId
                + ":"
                + (schemaCode == null || schemaCode.isBlank() ? "*" : schemaCode)
                + ":"
                + (lifecycleStatusCode == null || lifecycleStatusCode.isBlank() ? "*" : lifecycleStatusCode);
    }

    private String policyCode(ObjectExposurePolicyDecisionDto decision, String defaultCode) {
        return decision.code() == null || decision.code().isBlank() ? defaultCode : decision.code();
    }

    private String policyCode(ObjectExposureClassificationPolicyDecisionDto decision, String defaultCode) {
        return decision.code() == null || decision.code().isBlank() ? defaultCode : decision.code();
    }

    private String policyMessage(ObjectExposurePolicyDecisionDto decision, String defaultMessage) {
        return decision.message() == null || decision.message().isBlank() ? defaultMessage : decision.message();
    }

    private String policyMessage(ObjectExposureClassificationPolicyDecisionDto decision, String defaultMessage) {
        return decision.message() == null || decision.message().isBlank() ? defaultMessage : decision.message();
    }

    private static final class DefaultObjectExposurePolicyClient implements ObjectExposurePolicyClient {

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(ObjectExposureAccessPolicyRequestDto request) {
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }
    }
}
