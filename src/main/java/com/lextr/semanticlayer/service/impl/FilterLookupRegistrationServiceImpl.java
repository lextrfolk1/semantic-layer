package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupRegistrationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class FilterLookupRegistrationServiceImpl implements FilterLookupRegistrationService {

    private static final String GOVERNANCE_POLICY_CD = "GOV-FL-001";
    private static final String POLICY_SCOPE_CD = "FILTER_LOOKUP";
    private static final String GOVERNANCE_STATUS_CD = "REVIEW";
    private static final String HEALTH_STATUS_CD = "PENDING";
    private static final String LIFECYCLE_STATUS_CD = "ACTIVE";
    private static final String WORKFLOW_TASK_TYPE_CD = "FILTER_LOOKUP_REGISTRATION";
    private static final String WORKFLOW_ENTITY_TYPE_CD = "FILTER_LOOKUP";
    private static final String WORKFLOW_TASK_STATUS_CD = "PENDING";
    private static final String CHANGE_TYPE_CD = "REGISTERED";
    private static final int DEFAULT_MAX_INPUT_SET_SIZE = 500;
    private static final int DEFAULT_MAX_OUTPUT_ROWS = 10_000;
    private static final int DEFAULT_CACHE_TTL_MIN = 60;

    private final FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao;
    private final GovernancePolicyPresetReadDao governancePolicyPresetReadDao;
    private final FilterLookupPolicyClient filterLookupPolicyClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public FilterLookupRegistrationServiceImpl(
            ObjectProvider<FilterLookupRegistrationWriteDao> filterLookupRegistrationWriteDaoProvider,
            ObjectProvider<GovernancePolicyPresetReadDao> governancePolicyPresetReadDaoProvider,
            ObjectProvider<FilterLookupPolicyClient> filterLookupPolicyClientProvider,
            @Qualifier("semanticLayerTransactionOperations")
            ObjectProvider<TransactionOperations> transactionOperationsProvider
    ) {
        this(
                filterLookupRegistrationWriteDaoProvider.getIfAvailable(MissingFilterLookupRegistrationWriteDao::new),
                governancePolicyPresetReadDaoProvider.getIfAvailable(EmptyGovernancePolicyPresetReadDao::new),
                filterLookupPolicyClientProvider.getIfAvailable(
                        () -> request -> new FilterLookupPolicyDecisionDto(true, null, null)
                ),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    FilterLookupRegistrationServiceImpl(FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao,
                                        GovernancePolicyPresetReadDao governancePolicyPresetReadDao,
                                        FilterLookupPolicyClient filterLookupPolicyClient,
                                        TransactionOperations transactionOperations) {
        this.filterLookupRegistrationWriteDao = filterLookupRegistrationWriteDao;
        this.governancePolicyPresetReadDao = governancePolicyPresetReadDao;
        this.filterLookupPolicyClient = filterLookupPolicyClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public FilterLookupRegistrationResponseDto registerFilterLookup(FilterLookupRegistrationRequestDto request) {
        GovernancePolicyPresetRecord reviewFloorPolicy = findRequiredReviewFloorPolicy();
        Integer reviewPeriodFloorDays = parseReviewFloorDays(reviewFloorPolicy);

        validateReviewPeriodPolicy(request, reviewPeriodFloorDays);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Integer effectiveReviewPeriodDays = effectiveReviewPeriodDays(request.review_period_days_override(), reviewPeriodFloorDays);
        LocalDate nextReviewDueDate = now.toLocalDate().plusDays(effectiveReviewPeriodDays);

        try {
            return transactionOperations.execute(status -> persistRegistration(
                    request,
                    now,
                    nextReviewDueDate
            ));
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FilterLookupRegistrationServiceException("Unable to register filter lookup", exception);
        }
    }

    private GovernancePolicyPresetRecord findRequiredReviewFloorPolicy() {
        return governancePolicyPresetReadDao.findPolicyPreset(
                        GOVERNANCE_POLICY_CD,
                        POLICY_SCOPE_CD,
                        LocalDate.now(ZoneOffset.UTC)
                )
                .orElseThrow(() -> new FilterLookupRegistrationServiceException(
                        "Unable to resolve governance policy " + GOVERNANCE_POLICY_CD
                ));
    }

    private Integer parseReviewFloorDays(GovernancePolicyPresetRecord policyPreset) {
        try {
            return Integer.valueOf(policyPreset.default_value_txt());
        } catch (RuntimeException exception) {
            throw new FilterLookupRegistrationServiceException(
                    "Unable to parse governance policy value for " + policyPreset.policy_cd(),
                    exception
            );
        }
    }

    private void validateReviewPeriodPolicy(FilterLookupRegistrationRequestDto request, Integer reviewPeriodFloorDays) {
        FilterLookupPolicyDecisionDto decision = filterLookupPolicyClient.validateReviewPeriodFloor(
                new FilterLookupPolicyRequestDto(
                        request.client_id(),
                        request.lookup_cd(),
                        GOVERNANCE_POLICY_CD,
                        reviewPeriodFloorDays,
                        request.review_period_days_override()
                )
        );
        if (!decision.allowed()) {
            throw new PolicyViolationException(decision.code(), decision.message());
        }
    }

    private FilterLookupRegistrationResponseDto persistRegistration(FilterLookupRegistrationRequestDto request,
                                                                    OffsetDateTime now,
                                                                    LocalDate nextReviewDueDate) {
        SemanticFilterLookupRecord lookup = filterLookupRegistrationWriteDao.insertLookup(new SemanticFilterLookupWriteRequest(
                request.lookup_cd(),
                request.construction_type_cd(),
                request.manual_subtype_cd(),
                request.filter_obj(),
                request.filter_condition_txt(),
                request.filter_attr_cd(),
                request.validation_obj(),
                request.validation_attr_cd(),
                request.suggested_target_attr_cd(),
                request.execution_strategy_cd(),
                defaultInteger(request.max_input_set_size(), DEFAULT_MAX_INPUT_SET_SIZE),
                defaultInteger(request.max_output_rows(), DEFAULT_MAX_OUTPUT_ROWS),
                defaultInteger(request.cache_ttl_min(), DEFAULT_CACHE_TTL_MIN),
                request.review_period_days_override(),
                defaultBoolean(request.rules_eligible_flg(), true),
                defaultBoolean(request.qs_eligible_flg(), true),
                defaultBoolean(request.ai_eligible_flg(), false),
                defaultBoolean(request.replicate_to_ch_flg(), false),
                request.description_txt(),
                request.client_id(),
                GOVERNANCE_STATUS_CD,
                HEALTH_STATUS_CD,
                nextReviewDueDate,
                LIFECYCLE_STATUS_CD,
                now,
                request.registered_by(),
                now,
                request.registered_by()
        ));

        FilterLookupWorkflowTaskRecord workflowTask = filterLookupRegistrationWriteDao.insertWorkflowTask(
                new FilterLookupWorkflowTaskWriteRequest(
                        WORKFLOW_TASK_TYPE_CD,
                        WORKFLOW_ENTITY_TYPE_CD,
                        request.lookup_cd(),
                        WORKFLOW_TASK_STATUS_CD,
                        request.registered_by(),
                        now,
                        null,
                        nextReviewDueDate,
                        "Review filter lookup " + request.lookup_cd(),
                        request.client_id(),
                        null,
                        null,
                        null
                )
        );

        filterLookupRegistrationWriteDao.insertMetadataChangeHistory(new FilterLookupMetadataChangeHistoryWriteRequest(
                WORKFLOW_ENTITY_TYPE_CD,
                request.lookup_cd(),
                CHANGE_TYPE_CD,
                request.registered_by(),
                now,
                null,
                null,
                "Registered filter lookup " + request.lookup_cd()
        ));

        return new FilterLookupRegistrationResponseDto(
                lookup.id(),
                lookup.lookup_cd(),
                lookup.construction_type_cd(),
                lookup.review_period_days_override(),
                lookup.governance_status_cd(),
                lookup.health_status_cd(),
                lookup.next_review_due_dt(),
                lookup.lifecycle_status_cd(),
                workflowTask.id(),
                workflowTask.task_status_cd()
        );
    }

    private int defaultInteger(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Integer effectiveReviewPeriodDays(Integer reviewPeriodDaysOverride, Integer reviewPeriodFloorDays) {
        return reviewPeriodDaysOverride == null ? reviewPeriodFloorDays : reviewPeriodDaysOverride;
    }

    private static final class MissingFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {

        @Override
        public SemanticFilterLookupRecord insertLookup(SemanticFilterLookupWriteRequest request) {
            throw new SemanticLayerException("FilterLookupRegistrationWriteDao is not configured");
        }

        @Override
        public FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request) {
            throw new SemanticLayerException("FilterLookupRegistrationWriteDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
                FilterLookupMetadataChangeHistoryWriteRequest request
        ) {
            throw new SemanticLayerException("FilterLookupRegistrationWriteDao is not configured");
        }
    }

    private static final class EmptyGovernancePolicyPresetReadDao implements GovernancePolicyPresetReadDao {

        @Override
        public Optional<GovernancePolicyPresetRecord> findPolicyPreset(String policyCode,
                                                                       String policyScopeCode,
                                                                       LocalDate asOfDate) {
            return Optional.empty();
        }

        @Override
        public java.util.List<GovernancePolicyPresetRecord> findPolicyPresets(String policyScopeCode, LocalDate asOfDate) {
            return java.util.Collections.emptyList();
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
