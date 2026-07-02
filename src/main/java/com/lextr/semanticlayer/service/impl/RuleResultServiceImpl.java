package com.lextr.semanticlayer.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.RuleResultDao;
import com.lextr.semanticlayer.dto.DqRuleResultDto;
import com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.ExternalRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.RuleResultIngestResponseDto;
import com.lextr.semanticlayer.dto.RuleResultPolicyDecisionDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RuleResultServiceException;
import com.lextr.semanticlayer.model.ExternalRuleResultRecord;
import com.lextr.semanticlayer.model.ExternalRuleResultWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
import com.lextr.semanticlayer.service.DqRuleService;
import com.lextr.semanticlayer.service.RuleResultPolicyClient;
import com.lextr.semanticlayer.service.RuleResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

@Service
public class RuleResultServiceImpl implements RuleResultService {

    private static final Logger logger = LoggerFactory.getLogger(RuleResultServiceImpl.class);

    private static final String RESULT_ENTITY_TYPE_CD = "EXTERNAL_RULE_RESULT";
    private static final String AUDIT_INGESTED = "INGESTED";
    private static final String AUDIT_ROUTED_EDITCHECK = "ROUTED_EDITCHECK";
    private static final String LP24_ROUTE_TARGET = "LP-24";
    private static final String LOCAL_ROUTE_TARGET = "SELF";
    private static final String EDITCHECK_KIND_CD = "EDITCHECK";

    private final RuleResultDao ruleResultDao;
    private final RuleResultPolicyClient ruleResultPolicyClient;
    private final DqRuleService dqRuleService;
    private final TransactionOperations transactionOperations;
    private final ObjectMapper objectMapper;

    @Autowired
    public RuleResultServiceImpl(ObjectProvider<RuleResultDao> ruleResultDaoProvider,
                                 ObjectProvider<RuleResultPolicyClient> ruleResultPolicyClientProvider,
                                 ObjectProvider<DqRuleService> dqRuleServiceProvider,
                                 @Qualifier("semanticLayerTransactionOperations")
                                 ObjectProvider<TransactionOperations> transactionOperationsProvider,
                                 ObjectMapper objectMapper) {
        this(
                ruleResultDaoProvider.getIfAvailable(MissingRuleResultDao::new),
                ruleResultPolicyClientProvider.getIfAvailable(DefaultRuleResultPolicyClient::new),
                dqRuleServiceProvider.getIfAvailable(MissingDqRuleService::new),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new),
                objectMapper
        );
    }

    RuleResultServiceImpl(RuleResultDao ruleResultDao,
                          RuleResultPolicyClient ruleResultPolicyClient,
                          DqRuleService dqRuleService,
                          TransactionOperations transactionOperations,
                          ObjectMapper objectMapper) {
        this.ruleResultDao = ruleResultDao;
        this.ruleResultPolicyClient = ruleResultPolicyClient;
        this.dqRuleService = dqRuleService;
        this.transactionOperations = transactionOperations;
        this.objectMapper = objectMapper;
    }

    @Override
    public RuleResultIngestResponseDto ingestRuleResult(ExternalRuleResultIngestRequestDto request, String principalCd) {
        logger.debug("Ingesting rule result. clientId={}, outboundId={}, ruleRefCode={}, outputKindCode={}",
                request.client_id(), request.outbound_id(), request.rule_ref_cd(), request.output_kind_cd());
        RuleResultPolicyDecisionDto decision = ruleResultPolicyClient.validateIngest(request, principalCd);
        if (!decision.allowed()) {
            logger.warn("Rule result ingest denied. clientId={}, outboundId={}, ruleRefCode={}, policyCode={}",
                    request.client_id(), request.outbound_id(), request.rule_ref_cd(), decision.code());
            throw new PolicyViolationException(decision.code(), decision.message());
        }

        OffsetDateTime now = effectiveNow(request.observed_ts());
        String normalizedKind = normalizeKind(request.output_kind_cd());
        try {
            RuleResultIngestResponseDto response = transactionOperations.execute(status -> {
                if (EDITCHECK_KIND_CD.equals(normalizedKind)) {
                    return routeEditcheck(request, principalCd, normalizedKind, now);
                }
                return ingestExternalResult(request, principalCd, normalizedKind, now);
            });
            logger.info("Rule result ingested. clientId={}, outboundId={}, ruleRefCode={}, routeTargetCode={}",
                    request.client_id(), request.outbound_id(), request.rule_ref_cd(), response.route_target_cd());
            return response;
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            logger.error("Rule result ingest failed. clientId={}, outboundId={}, ruleRefCode={}, errorMessage={}",
                    request.client_id(), request.outbound_id(), request.rule_ref_cd(), exception.getMessage(), exception);
            throw new RuleResultServiceException("Unable to ingest rule result", exception);
        }
    }

    private RuleResultIngestResponseDto ingestExternalResult(ExternalRuleResultIngestRequestDto request,
                                                             String principalCd,
                                                             String outputKindCd,
                                                             OffsetDateTime now) {
        ExternalRuleResultRecord result = ruleResultDao.insertResult(new ExternalRuleResultWriteRequest(
                request.client_id(),
                request.outbound_id(),
                request.rule_ref_cd(),
                outputKindCd,
                request.output_payload_jsonb().toString(),
                now,
                now,
                principalCd,
                now,
                principalCd
        ));

        ruleResultDao.insertMetadataChangeHistory(new ObjectExposureAccessAuditWriteRequest(
                RESULT_ENTITY_TYPE_CD,
                auditEntityRef(request),
                AUDIT_INGESTED,
                principalCd,
                now,
                "Ingested " + outputKindCd + " rule result for outbound " + request.outbound_id() + " and rule " + request.rule_ref_cd()
        ));

        return toResponse(result, null, LOCAL_ROUTE_TARGET);
    }

    private RuleResultIngestResponseDto routeEditcheck(ExternalRuleResultIngestRequestDto request,
                                                       String principalCd,
                                                       String outputKindCd,
                                                       OffsetDateTime now) {
        logger.info("Routing rule result to DQ ingest. clientId={}, outboundId={}, ruleRefCode={}",
                request.client_id(), request.outbound_id(), request.rule_ref_cd());
        DqRuleResultIngestRequestDto dqRequest = toDqRuleResultIngestRequest(request, principalCd);
        DqRuleResultDto routedResult = dqRuleService.ingestResult(dqRequest, principalCd);

        ruleResultDao.insertMetadataChangeHistory(new ObjectExposureAccessAuditWriteRequest(
                RESULT_ENTITY_TYPE_CD,
                auditEntityRef(request),
                AUDIT_ROUTED_EDITCHECK,
                principalCd,
                now,
                "Routed EDITCHECK rule result for outbound " + request.outbound_id() + " and rule " + request.rule_ref_cd() + " to LP-24"
        ));

        return new RuleResultIngestResponseDto(
                null,
                routedResult.id(),
                request.client_id(),
                request.outbound_id(),
                request.rule_ref_cd(),
                outputKindCd,
                LP24_ROUTE_TARGET,
                request.output_payload_jsonb(),
                routedResult.observed_ts(),
                routedResult.created_ts(),
                routedResult.created_by(),
                routedResult.updated_ts(),
                routedResult.updated_by()
        );
    }

    private RuleResultIngestResponseDto toResponse(ExternalRuleResultRecord result,
                                                   Long dqResultId,
                                                   String routeTargetCd) {
        return new RuleResultIngestResponseDto(
                result.id(),
                dqResultId,
                result.client_id(),
                result.outbound_id(),
                result.rule_ref_cd(),
                result.output_kind_cd(),
                routeTargetCd,
                parseJson(result.output_payload_jsonb()),
                result.observed_ts(),
                result.created_ts(),
                result.created_by(),
                result.updated_ts(),
                result.updated_by()
        );
    }

    private DqRuleResultIngestRequestDto toDqRuleResultIngestRequest(ExternalRuleResultIngestRequestDto request,
                                                                     String principalCd) {
        JsonNode payload = request.output_payload_jsonb();
        String ruleCode = requiredText(payload, "rule_cd", request.rule_ref_cd());
        String logicalAttributeCode = requiredText(payload, "logical_attribute_cd", null);
        String observedValueText = requiredText(payload, "observed_value_txt", null);
        String resultStatusCode = requiredText(payload, "result_status_cd", null);
        String expectedValueText = optionalText(payload, "expected_value_txt");
        String resultReasonText = optionalText(payload, "result_reason_txt");
        OffsetDateTime observedTs = optionalTimestamp(payload, "observed_ts").orElseGet(() -> effectiveNow(request.observed_ts()));
        return new DqRuleResultIngestRequestDto(
                request.client_id(),
                ruleCode,
                logicalAttributeCode,
                observedValueText,
                expectedValueText,
                resultStatusCode,
                resultReasonText,
                observedTs,
                principalCd
        );
    }

    private static String auditEntityRef(ExternalRuleResultIngestRequestDto request) {
        return request.outbound_id() + ":" + request.rule_ref_cd();
    }

    private JsonNode parseJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonText);
        } catch (IOException exception) {
            logger.error("Failed to parse stored rule result payload. errorMessage={}", exception.getMessage(), exception);
            throw new RuleResultServiceException("Unable to parse stored rule result payload", exception);
        }
    }

    private static String normalizeKind(String outputKindCd) {
        return outputKindCd == null ? null : outputKindCd.trim().toUpperCase(Locale.ROOT);
    }

    private static String requiredText(JsonNode node, String fieldName, String fallback) {
        String value = node != null && node.hasNonNull(fieldName) ? node.get(fieldName).asText() : fallback;
        if (value == null || value.isBlank()) {
            throw new RuleResultServiceException("EDITCHECK payload is missing required field " + fieldName);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        String value = node.get(fieldName).asText();
        return value.isBlank() ? null : value;
    }

    private static Optional<OffsetDateTime> optionalTimestamp(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return Optional.empty();
        }
        String rawValue = node.get(fieldName).asText();
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(OffsetDateTime.parse(rawValue));
    }

    private static OffsetDateTime effectiveNow(OffsetDateTime observedTs) {
        return observedTs == null ? OffsetDateTime.now(ZoneOffset.UTC) : observedTs;
    }

    private static final class DefaultRuleResultPolicyClient implements RuleResultPolicyClient {
    }

    private static final class MissingRuleResultDao implements RuleResultDao {

        @Override
        public ExternalRuleResultRecord insertResult(ExternalRuleResultWriteRequest request) {
            throw new RuleResultServiceException("RuleResultDao is not configured");
        }

        @Override
        public void insertMetadataChangeHistory(ObjectExposureAccessAuditWriteRequest request) {
            throw new RuleResultServiceException("RuleResultDao is not configured");
        }
    }

    private static final class MissingDqRuleService implements DqRuleService {

        @Override
        public java.util.List<com.lextr.semanticlayer.dto.DqRuleCatalogDto> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
            throw new RuleResultServiceException("DqRuleService is not configured");
        }

        @Override
        public com.lextr.semanticlayer.dto.DqRuleCatalogDto findRule(String clientId, String ruleCode) {
            throw new RuleResultServiceException("DqRuleService is not configured");
        }

        @Override
        public java.util.List<com.lextr.semanticlayer.dto.DqRuleAttributeDto> findRuleAttributes(String clientId, String ruleCode) {
            throw new RuleResultServiceException("DqRuleService is not configured");
        }

        @Override
        public java.util.List<com.lextr.semanticlayer.dto.DqRuleResultDto> findRuleResults(String clientId, String logicalAttributeCode) {
            throw new RuleResultServiceException("DqRuleService is not configured");
        }

        @Override
        public com.lextr.semanticlayer.dto.DqRuleResultDto ingestResult(DqRuleResultIngestRequestDto request, String principalCd) {
            throw new RuleResultServiceException("DqRuleService is not configured");
        }

        @Override
        public java.util.List<com.lextr.semanticlayer.dto.WorkflowTaskResponseDto> requestRules(com.lextr.semanticlayer.dto.DqRuleRequestDto request) {
            throw new RuleResultServiceException("DqRuleService is not configured");
        }

        @Override
        public com.lextr.semanticlayer.dto.WorkflowTaskResponseDto findRequest(String clientId, java.util.UUID workflowTaskId) {
            throw new RuleResultServiceException("DqRuleService is not configured");
        }
    }

    private static final class NoOpTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new NoOpTransactionStatus());
        }
    }

    private static final class NoOpTransactionStatus implements TransactionStatus {

        @Override
        public boolean isNewTransaction() {
            return false;
        }

        @Override
        public boolean hasSavepoint() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public boolean isRollbackOnly() {
            return false;
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public Object createSavepoint() {
            return null;
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) {
        }

        @Override
        public void releaseSavepoint(Object savepoint) {
        }
    }
}
