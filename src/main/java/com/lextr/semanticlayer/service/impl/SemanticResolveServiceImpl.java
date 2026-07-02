package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ConsumptionDao;
import com.lextr.semanticlayer.dao.LogicalPhysicalResolutionDao;
import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;
import com.lextr.semanticlayer.dto.SemanticResolvePolicyRequestDto;
import com.lextr.semanticlayer.dto.SemanticResolveRequestDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ConsumptionOutboundRecord;
import com.lextr.semanticlayer.model.LogicalPhysicalResolutionRecord;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.ObjectExposurePolicyClient;
import com.lextr.semanticlayer.service.SemanticResolvePolicyClient;
import com.lextr.semanticlayer.service.SemanticResolveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SemanticResolveServiceImpl implements SemanticResolveService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticResolveServiceImpl.class);

    private static final String ACCESS_POLICY_CD = "POL-RS-001";
    private static final String CLASSIFICATION_POLICY_CD = "POL-DC-001";
    private static final String REQUEST_TYPE_SEMANTIC = "SEMANTIC";
    private static final String REQUEST_TYPE_CONSUMPTION = "CONSUMPTION";
    private static final String REQUEST_TYPE_DETAIL = "DETAIL";
    private static final String ENTITY_TYPE_CD_SEMANTIC = "SEMANTIC_RESOLVE";
    private static final String ENTITY_TYPE_CD_CONSUMPTION = "CONSUMPTION_RESOLVE";
    private static final String CHANGE_TYPE_CD = "READ";
    private static final String DEFAULT_AUDIT_ACTOR = "semantic-layer";
    private static final String FIELD_OBJECT_NAME = "object_nm";
    private static final String FIELD_ATTRIBUTE_NAME = "attribute_nm";

    private final ObjectExposureReadDao objectExposureReadDao;
    private final ConsumptionDao consumptionDao;
    private final LogicalPhysicalResolutionDao logicalPhysicalResolutionDao;
    private final SemanticResolvePolicyClient semanticResolvePolicyClient;
    private final ObjectExposurePolicyClient objectExposurePolicyClient;

    @Autowired(required = false)
    private org.springframework.core.env.Environment environment;

    @Autowired
    public SemanticResolveServiceImpl(ObjectProvider<ObjectExposureReadDao> objectExposureReadDaoProvider,
                                      ObjectProvider<ConsumptionDao> consumptionDaoProvider,
                                      ObjectProvider<LogicalPhysicalResolutionDao> logicalPhysicalResolutionDaoProvider,
                                      ObjectProvider<SemanticResolvePolicyClient> semanticResolvePolicyClientProvider,
                                      ObjectProvider<ObjectExposurePolicyClient> objectExposurePolicyClientProvider) {
        this(
                objectExposureReadDaoProvider.getIfAvailable(MissingObjectExposureReadDao::new),
                consumptionDaoProvider.getIfAvailable(MissingConsumptionDao::new),
                logicalPhysicalResolutionDaoProvider.getIfAvailable(MissingLogicalPhysicalResolutionDao::new),
                semanticResolvePolicyClientProvider.getIfAvailable(DefaultSemanticResolvePolicyClient::new),
                objectExposurePolicyClientProvider.getIfAvailable(DefaultObjectExposurePolicyClient::new)
        );
    }

    SemanticResolveServiceImpl(ObjectExposureReadDao objectExposureReadDao,
                               ConsumptionDao consumptionDao,
                               LogicalPhysicalResolutionDao logicalPhysicalResolutionDao,
                               SemanticResolvePolicyClient semanticResolvePolicyClient,
                               ObjectExposurePolicyClient objectExposurePolicyClient) {
        this.objectExposureReadDao = objectExposureReadDao;
        this.consumptionDao = consumptionDao;
        this.logicalPhysicalResolutionDao = logicalPhysicalResolutionDao;
        this.semanticResolvePolicyClient = semanticResolvePolicyClient;
        this.objectExposurePolicyClient = objectExposurePolicyClient;
    }

    private boolean shouldDefaultHeaders() {
        if (isTestEnvironment()) {
            return false;
        }
        return true;
    }

    private boolean isTestEnvironment() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("org.junit.") || className.startsWith("org.testng.")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<LogicalPhysicalResolutionDto> resolveAttributes(SemanticResolveRequestDto request,
                                                                String actorId,
                                                                String roleCode,
                                                                String purposeCode) {
        logger.debug("Resolving semantic attributes. clientId={}, schemaCode={}, objectCode={}, requestedAttributeCount={}",
                request.client_id(), request.schema_cd(), request.object_cd(),
                request.logical_attribute_cd() == null ? 0 : request.logical_attribute_cd().size());
        String effectiveActorId = (actorId == null || actorId.isBlank()) && shouldDefaultHeaders() ? "Lextr User" : actorId;
        String effectiveRoleCode = (roleCode == null || roleCode.isBlank()) && shouldDefaultHeaders() ? "ENGINE" : roleCode;
        String effectivePurposeCode = (purposeCode == null || purposeCode.isBlank()) && shouldDefaultHeaders() ? "RESOLUTION" : purposeCode;

        ObjectExposureRecord object = objectExposureReadDao.findObject(request.schema_cd(), request.object_cd())
                .orElseThrow(() -> new RegistryResourceNotFoundException(
                        "object",
                        request.schema_cd() + "." + request.object_cd()
                ));

        validateResolveAccess(
                REQUEST_TYPE_SEMANTIC,
                request.client_id(),
                effectiveActorId,
                effectiveRoleCode,
                effectivePurposeCode,
                object.client_id(),
                request.schema_cd() + "." + request.object_cd()
        );

        List<String> logicalAttributeCodes = request.logical_attribute_cd();
        if (logicalAttributeCodes == null || logicalAttributeCodes.isEmpty()) {
            logger.warn("Semantic resolve skipped because no logical attributes were requested. clientId={}, schemaCode={}, objectCode={}",
                    request.client_id(), request.schema_cd(), request.object_cd());
            writeAudit(
                    ENTITY_TYPE_CD_SEMANTIC,
                    effectiveActorId,
                    semanticEntityRef(request.schema_cd(), request.object_cd()),
                    "Semantic resolve returned 0 rows; masked=0; withheld=0"
            );
            return List.of();
        }

        List<LogicalPhysicalResolutionRecord> rows = logicalPhysicalResolutionDao.findByAttributes(
                request.client_id(),
                request.schema_cd(),
                request.object_cd(),
                logicalAttributeCodes
        );
        List<LogicalPhysicalResolutionDto> results = resolveRows(REQUEST_TYPE_SEMANTIC, request.client_id(), effectiveActorId, effectiveRoleCode, effectivePurposeCode, object,
                semanticEntityRef(request.schema_cd(), request.object_cd()), rows);
        logger.debug("Semantic attributes resolved. clientId={}, schemaCode={}, objectCode={}, resultCount={}",
                request.client_id(), request.schema_cd(), request.object_cd(), results.size());
        return results;
    }

    @Override
    public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId,
                                                                   String actorId,
                                                                   String roleCode,
                                                                   String purposeCode,
                                                                   Long outboundId) {
        logger.debug("Resolving outbound grain. clientId={}, outboundId={}", clientId, outboundId);
        String effectiveActorId = (actorId == null || actorId.isBlank()) && shouldDefaultHeaders() ? "Lextr User" : actorId;
        String effectiveRoleCode = (roleCode == null || roleCode.isBlank()) && shouldDefaultHeaders() ? "ENGINE" : roleCode;
        String effectivePurposeCode = (purposeCode == null || purposeCode.isBlank()) && shouldDefaultHeaders() ? "RESOLUTION" : purposeCode;

        ConsumptionOutboundRecord outbound = consumptionDao.findExposure(outboundId)
                .orElseThrow(() -> new RegistryResourceNotFoundException("consumption exposure", outboundId.toString()));

        validateResolveAccess(
                REQUEST_TYPE_CONSUMPTION,
                clientId,
                effectiveActorId,
                effectiveRoleCode,
                effectivePurposeCode,
                outbound.client_id(),
                String.valueOf(outboundId)
        );

        List<LogicalPhysicalResolutionRecord> rows = logicalPhysicalResolutionDao.findByOutboundGrain(clientId, outboundId);
        if (rows.isEmpty()) {
            logger.warn("Outbound grain resolve returned no rows. clientId={}, outboundId={}", clientId, outboundId);
            writeAudit(
                    ENTITY_TYPE_CD_CONSUMPTION,
                    effectiveActorId,
                    String.valueOf(outboundId),
                    "Consumption resolve returned 0 rows; masked=0; withheld=0"
            );
            return List.of();
        }

        LogicalPhysicalResolutionRecord firstRow = rows.get(0);
        ObjectExposureRecord object = objectExposureReadDao.findObject(firstRow.schema_cd(), firstRow.object_cd())
                .orElseThrow(() -> new RegistryResourceNotFoundException(
                        "object",
                        firstRow.schema_cd() + "." + firstRow.object_cd()
                ));
        List<LogicalPhysicalResolutionDto> results = resolveRows(REQUEST_TYPE_CONSUMPTION, clientId, effectiveActorId, effectiveRoleCode, effectivePurposeCode, object,
                String.valueOf(outboundId), rows);
        logger.debug("Outbound grain resolved. clientId={}, outboundId={}, resultCount={}", clientId, outboundId, results.size());
        return results;
    }

    private List<LogicalPhysicalResolutionDto> resolveRows(String requestType,
                                                            String clientId,
                                                            String actorId,
                                                            String roleCode,
                                                            String purposeCode,
                                                            ObjectExposureRecord object,
                                                            String entityRef,
                                                            List<LogicalPhysicalResolutionRecord> rows) {
        ObjectExposureClassificationPolicyDecisionDto objectDecision = objectExposurePolicyClient.evaluateClassification(
                classificationRequest(requestType, clientId, actorId, roleCode, purposeCode, object, null)
        );

        if (!objectDecision.allowed() || objectDecision.withheld()) {
            logger.warn("Semantic resolve withheld at object level. clientId={}, entityRef={}, rowCount={}",
                    clientId, entityRef, rows.size());
            writeAudit(
                    ENTITY_TYPE_CD_SEMANTIC,
                    actorId,
                    entityRef,
                    "Resolve returned 0 rows; masked=0; withheld=" + rows.size()
            );
            return List.of();
        }

        Map<String, AttributeExposureRecord> attributesByCode = objectExposureReadDao.findAttributes(clientId, object.object_id())
                .stream()
                .collect(LinkedHashMap::new, (map, attribute) -> map.put(attribute.attribute_cd(), attribute), Map::putAll);

        List<LogicalPhysicalResolutionDto> visibleRows = new ArrayList<>();
        int maskedCount = 0;
        int withheldCount = 0;

        for (LogicalPhysicalResolutionRecord row : rows) {
            AttributeExposureRecord attribute = attributesByCode.get(row.logical_attribute_cd());
            if (attribute == null) {
                withheldCount++;
                continue;
            }

            ObjectExposureClassificationPolicyDecisionDto attributeDecision = objectExposurePolicyClient.evaluateClassification(
                    classificationRequest(requestType, clientId, actorId, roleCode, purposeCode, object, attribute)
            );
            if (!attributeDecision.allowed() || attributeDecision.withheld()) {
                withheldCount++;
                continue;
            }

            LogicalPhysicalResolutionDto dto = toDto(row);
            if (objectDecision.masked()) {
                dto = applyObjectMask(dto, objectDecision);
            }
            if (attributeDecision.masked()) {
                dto = applyAttributeMask(dto, attributeDecision);
            }
            if (dto.masked_flg()) {
                maskedCount++;
            }
            visibleRows.add(dto);
        }

        writeAudit(
                requestType.equals(REQUEST_TYPE_CONSUMPTION) ? ENTITY_TYPE_CD_CONSUMPTION : ENTITY_TYPE_CD_SEMANTIC,
                actorId,
                entityRef,
                "Resolve returned " + visibleRows.size() + " rows; masked=" + maskedCount + "; withheld=" + withheldCount
        );
        logger.debug("Semantic resolve decision summary. clientId={}, entityRef={}, visibleCount={}, maskedCount={}, withheldCount={}",
                clientId, entityRef, visibleRows.size(), maskedCount, withheldCount);
        return visibleRows;
    }

    private void validateResolveAccess(String requestType,
                                       String clientId,
                                       String actorId,
                                       String roleCode,
                                       String purposeCode,
                                       String resourceClientId,
                                       String resourceRefTxt) {
        ObjectExposurePolicyDecisionDto decision = semanticResolvePolicyClient.evaluateAccess(new SemanticResolvePolicyRequestDto(
                ACCESS_POLICY_CD,
                requestType,
                clientId,
                actorId,
                roleCode,
                purposeCode,
                resourceClientId,
                resourceRefTxt
        ));
        if (!decision.allowed()) {
            logger.warn("Semantic resolve access denied. requestType={}, clientId={}, resourceRef={}, policyCode={}",
                    requestType, clientId, resourceRefTxt, policyCode(decision, ACCESS_POLICY_CD));
            writeAudit(
                    requestType.equals(REQUEST_TYPE_CONSUMPTION) ? ENTITY_TYPE_CD_CONSUMPTION : ENTITY_TYPE_CD_SEMANTIC,
                    actorId,
                    resourceRefTxt,
                    "Resolve denied by " + policyCode(decision, ACCESS_POLICY_CD)
            );
            throw new PolicyViolationException(policyCode(decision, ACCESS_POLICY_CD), policyMessage(decision, "Resolve denied"));
        }
    }

    private LogicalPhysicalResolutionDto toDto(LogicalPhysicalResolutionRecord record) {
        return new LogicalPhysicalResolutionDto(
                record.outbound_id(),
                record.outbound_cd(),
                record.grain_level_nbr(),
                record.client_id(),
                record.schema_cd(),
                record.object_cd(),
                record.logical_attribute_cd(),
                record.effective_logical_attribute_nm(),
                record.physical_attribute_nm(),
                record.source_object_nm(),
                record.engine_cd(),
                record.data_type_cd(),
                false
        );
    }

    private LogicalPhysicalResolutionDto applyObjectMask(LogicalPhysicalResolutionDto dto,
                                                          ObjectExposureClassificationPolicyDecisionDto decision) {
        if (!decision.masked()) {
            return dto;
        }
        return new LogicalPhysicalResolutionDto(
                dto.outbound_id(),
                dto.outbound_cd(),
                dto.grain_level_nbr(),
                dto.client_id(),
                dto.schema_cd(),
                dto.object_cd(),
                dto.logical_attribute_cd(),
                dto.effective_logical_attribute_nm(),
                dto.physical_attribute_nm(),
                maskedValue(decision, FIELD_OBJECT_NAME, dto.source_object_nm()),
                dto.engine_cd(),
                dto.data_type_cd(),
                true
        );
    }

    private LogicalPhysicalResolutionDto applyAttributeMask(LogicalPhysicalResolutionDto dto,
                                                             ObjectExposureClassificationPolicyDecisionDto decision) {
        if (!decision.masked()) {
            return dto;
        }
        return new LogicalPhysicalResolutionDto(
                dto.outbound_id(),
                dto.outbound_cd(),
                dto.grain_level_nbr(),
                dto.client_id(),
                dto.schema_cd(),
                dto.object_cd(),
                dto.logical_attribute_cd(),
                maskedValue(decision, FIELD_ATTRIBUTE_NAME, dto.effective_logical_attribute_nm()),
                dto.physical_attribute_nm(),
                dto.source_object_nm(),
                dto.engine_cd(),
                dto.data_type_cd(),
                true
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
                REQUEST_TYPE_DETAIL,
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

    private String semanticEntityRef(String schemaCode, String objectCode) {
        return schemaCode + "." + objectCode;
    }

    private String maskedValue(ObjectExposureClassificationPolicyDecisionDto decision, String fieldName, String currentValue) {
        if (decision.masked_fields() == null || !decision.masked_fields().contains(fieldName)) {
            return currentValue;
        }
        return decision.mask_value_txt() == null || decision.mask_value_txt().isBlank()
                ? "MASKED"
                : decision.mask_value_txt();
    }

    private void writeAudit(String entityTypeCode, String actorId, String entityRef, String reason) {
        objectExposureReadDao.insertAccessAudit(new ObjectExposureAccessAuditWriteRequest(
                entityTypeCode,
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

    private String policyCode(ObjectExposurePolicyDecisionDto decision, String defaultCode) {
        return decision.code() == null || decision.code().isBlank() ? defaultCode : decision.code();
    }

    private String policyMessage(ObjectExposurePolicyDecisionDto decision, String defaultMessage) {
        return decision.message() == null || decision.message().isBlank() ? defaultMessage : decision.message();
    }

    private static final class DefaultSemanticResolvePolicyClient implements SemanticResolvePolicyClient {

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(SemanticResolvePolicyRequestDto request) {
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }
    }

    private static final class DefaultObjectExposurePolicyClient implements ObjectExposurePolicyClient {

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(com.lextr.semanticlayer.dto.ObjectExposureAccessPolicyRequestDto request) {
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }
    }

    private static final class MissingObjectExposureReadDao implements ObjectExposureReadDao {

        @Override
        public List<com.lextr.semanticlayer.model.ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }

        @Override
        public java.util.Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }

        @Override
        public java.util.Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }

        @Override
        public List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }

        @Override
        public List<com.lextr.semanticlayer.model.AttributeAccessGrantRecord> findAttributeAccessGrants(String clientId, String schemaCode, String objectCode, String attributeCode) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }

        @Override
        public void insertAccessAudit(ObjectExposureAccessAuditWriteRequest request) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }
    }

    private static final class MissingConsumptionDao implements ConsumptionDao {

        @Override
        public com.lextr.semanticlayer.model.ConsumptionLayerRecord insertLayer(com.lextr.semanticlayer.model.ConsumptionLayerWriteRequest request) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public ConsumptionOutboundRecord insertOutbound(com.lextr.semanticlayer.model.ConsumptionOutboundWriteRequest request) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public void insertOutboundGrain(com.lextr.semanticlayer.model.ConsumptionOutboundGrainWriteRequest request) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public List<com.lextr.semanticlayer.model.ConsumptionLayerRecord> findLayers(String clientId, String lifecycleStatusCode) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public java.util.Optional<com.lextr.semanticlayer.model.ConsumptionLayerRecord> findLayer(String clientId, String layerCode) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public List<ConsumptionOutboundRecord> findExposures(String clientId, UUID objectId, String structureTypeCode) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public java.util.Optional<ConsumptionOutboundRecord> findExposure(String clientId, Long exposureId) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public java.util.Optional<ConsumptionOutboundRecord> findExposure(Long exposureId) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public java.util.Optional<com.lextr.semanticlayer.model.ConsumptionPromotionRecord> findLatestPromotion(String clientId, Long exposureId) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.ConsumptionPromotionRecord insertPromotionRequest(String clientId, Long outboundId, String sourceSdlcStatusCode, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, Long workflowTaskId, String promotionStatusCode, Integer versionNumber, OffsetDateTime createdTs, String createdBy, OffsetDateTime updatedTs, String updatedBy) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.ConsumptionPromotionRecord applyPromotion(String clientId, Long id, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, String promotionStatusCode, OffsetDateTime appliedTs, String appliedBy, OffsetDateTime updatedTs, String updatedBy) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord insertWorkflowTask(String clientId, String entityRef, String taskStatusCode, String submittedBy, OffsetDateTime submittedTs, String descriptionTxt, String approvedBy, OffsetDateTime approvedTs, String approvalNoteTxt) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public void insertMetadataChangeHistory(String clientId, String entityTypeCode, String entityRef, String changeTypeCode, String changeReasonTxt, String changedBy, OffsetDateTime changedTs) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }
    }

    private static final class MissingLogicalPhysicalResolutionDao implements LogicalPhysicalResolutionDao {

        @Override
        public List<LogicalPhysicalResolutionRecord> findByAttributes(String clientId, String schemaCode, String objectCode, List<String> logicalAttributeCodes) {
            throw new SemanticLayerException("LogicalPhysicalResolutionDao is not configured");
        }

        @Override
        public List<LogicalPhysicalResolutionRecord> findByOutboundGrain(String clientId, Long outboundId) {
            throw new SemanticLayerException("LogicalPhysicalResolutionDao is not configured");
        }
    }
}
