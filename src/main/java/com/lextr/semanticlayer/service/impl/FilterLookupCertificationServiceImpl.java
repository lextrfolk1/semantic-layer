package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.FilterLookupCertificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupCertificationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.exception.FilterLookupCertificationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupCertificationWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupCertificationService;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class FilterLookupCertificationServiceImpl implements FilterLookupCertificationService {

    private static final Logger logger = LoggerFactory.getLogger(FilterLookupCertificationServiceImpl.class);

    private static final String GOVERNANCE_POLICY_CD = "GOV-FL-001";
    private static final String POLICY_SCOPE_CD = "FILTER_LOOKUP";
    private static final String HEALTH_STATUS_CD = "HEALTHY";
    private static final String CHANGE_TYPE_CD = "CERTIFIED";
    private static final String ENTITY_TYPE_CD = "FILTER_LOOKUP";
    private static final String GOV_DEFAULT = "GOV_DEFAULT";
    private static final String LOOKUP_OVERRIDE = "LOOKUP_OVERRIDE";
    private static final String GOV_ENFORCED = "GOV_ENFORCED";

    private final FilterLookupReadDao filterLookupReadDao;
    private final FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao;
    private final GovernancePolicyPresetReadDao governancePolicyPresetReadDao;
    private final FilterLookupPolicyClient filterLookupPolicyClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public FilterLookupCertificationServiceImpl(
            ObjectProvider<FilterLookupReadDao> filterLookupReadDaoProvider,
            ObjectProvider<FilterLookupRegistrationWriteDao> filterLookupRegistrationWriteDaoProvider,
            ObjectProvider<GovernancePolicyPresetReadDao> governancePolicyPresetReadDaoProvider,
            ObjectProvider<FilterLookupPolicyClient> filterLookupPolicyClientProvider,
            @Qualifier("semanticLayerTransactionOperations")
            ObjectProvider<TransactionOperations> transactionOperationsProvider
    ) {
        this(
                filterLookupReadDaoProvider.getIfAvailable(MissingFilterLookupReadDao::new),
                filterLookupRegistrationWriteDaoProvider.getIfAvailable(MissingFilterLookupRegistrationWriteDao::new),
                governancePolicyPresetReadDaoProvider.getIfAvailable(EmptyGovernancePolicyPresetReadDao::new),
                filterLookupPolicyClientProvider.getIfAvailable(
                        () -> request -> new FilterLookupPolicyDecisionDto(true, null, null)
                ),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    FilterLookupCertificationServiceImpl(FilterLookupReadDao filterLookupReadDao,
                                         FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao,
                                         GovernancePolicyPresetReadDao governancePolicyPresetReadDao,
                                         FilterLookupPolicyClient filterLookupPolicyClient,
                                         TransactionOperations transactionOperations) {
        this.filterLookupReadDao = filterLookupReadDao;
        this.filterLookupRegistrationWriteDao = filterLookupRegistrationWriteDao;
        this.governancePolicyPresetReadDao = governancePolicyPresetReadDao;
        this.filterLookupPolicyClient = filterLookupPolicyClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public FilterLookupEffectiveReviewDto certifyLookup(String lookupCode, FilterLookupCertificationRequestDto request) {
        logger.debug("Certifying filter lookup. clientId={}, lookupCode={}", request.client_id(), lookupCode);
        SemanticFilterLookupRecord currentLookup = findRequiredLookup(request.client_id(), lookupCode);
        int reviewPeriodFloorDays = findReviewPeriodFloorDays();
        long staleValueCount = filterLookupReadDao.countStaleValues(request.client_id(), lookupCode);

        validateCertificationPolicy(currentLookup, request, staleValueCount);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate nextReviewDueDate = now.toLocalDate().plusDays(
                effectiveReviewPeriodDays(currentLookup.review_period_days_override(), reviewPeriodFloorDays)
        );

        try {
            FilterLookupEffectiveReviewDto review = transactionOperations.execute(status -> persistCertification(
                    currentLookup,
                    request,
                    now,
                    nextReviewDueDate,
                    reviewPeriodFloorDays
            ));
            logger.info("Filter lookup certified. clientId={}, lookupCode={}, healthStatusCode={}, nextReviewDueDate={}",
                    request.client_id(), lookupCode, review.health_status_cd(), review.next_review_due_dt());
            return review;
        } catch (PolicyViolationException exception) {
            logger.warn("Filter lookup certification denied. clientId={}, lookupCode={}, errorMessage={}",
                    request.client_id(), lookupCode, exception.getMessage(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            logger.error("Filter lookup certification failed. clientId={}, lookupCode={}, errorMessage={}",
                    request.client_id(), lookupCode, exception.getMessage(), exception);
            throw new FilterLookupCertificationServiceException("Unable to certify filter lookup", exception);
        }
    }

    private SemanticFilterLookupRecord findRequiredLookup(String clientId, String lookupCode) {
        return filterLookupReadDao.findLookup(clientId, lookupCode)
                .orElseThrow(() -> new RegistryResourceNotFoundException("filter lookup", lookupCode));
    }

    private int findReviewPeriodFloorDays() {
        GovernancePolicyPresetRecord policy = governancePolicyPresetReadDao.findPolicyPreset(
                        GOVERNANCE_POLICY_CD,
                        POLICY_SCOPE_CD,
                        LocalDate.now(ZoneOffset.UTC)
                )
                .orElseThrow(() -> new FilterLookupCertificationServiceException(
                        "Unable to resolve governance policy " + GOVERNANCE_POLICY_CD
                ));
        try {
            int reviewPeriodFloorDays = Integer.parseInt(policy.default_value_txt());
            logger.debug("Resolved filter lookup certification review floor. policyCode={}, reviewPeriodFloorDays={}",
                    policy.policy_cd(), reviewPeriodFloorDays);
            return reviewPeriodFloorDays;
        } catch (RuntimeException exception) {
            logger.error("Failed to parse filter lookup certification policy. policyCode={}, errorMessage={}",
                    policy.policy_cd(), exception.getMessage(), exception);
            throw new FilterLookupCertificationServiceException(
                    "Unable to parse governance policy value for " + policy.policy_cd(),
                    exception
            );
        }
    }

    private void validateCertificationPolicy(SemanticFilterLookupRecord lookup,
                                             FilterLookupCertificationRequestDto request,
                                             long staleValueCount) {
        FilterLookupPolicyDecisionDto decision = filterLookupPolicyClient.validateCertification(
                new FilterLookupCertificationPolicyRequestDto(
                        request.client_id(),
                        lookup.lookup_cd(),
                        request.certified_by(),
                        lookup.health_status_cd(),
                        staleValueCount
                )
        );
        if (!decision.allowed()) {
            logger.warn("Filter lookup certification policy denied. clientId={}, lookupCode={}, staleValueCount={}, policyCode={}",
                    request.client_id(), lookup.lookup_cd(), staleValueCount, decision.code());
            throw new PolicyViolationException(decision.code(), decision.message());
        }
    }

    private FilterLookupEffectiveReviewDto persistCertification(SemanticFilterLookupRecord currentLookup,
                                                                FilterLookupCertificationRequestDto request,
                                                                OffsetDateTime now,
                                                                LocalDate nextReviewDueDate,
                                                                int reviewPeriodFloorDays) {
        SemanticFilterLookupRecord certifiedLookup = filterLookupRegistrationWriteDao.certifyLookup(
                new FilterLookupCertificationWriteRequest(
                        request.client_id(),
                        currentLookup.lookup_cd(),
                        HEALTH_STATUS_CD,
                        now,
                        request.certified_by(),
                        nextReviewDueDate,
                        now,
                        request.certified_by()
                )
        );

        filterLookupRegistrationWriteDao.insertMetadataChangeHistory(new FilterLookupMetadataChangeHistoryWriteRequest(
                ENTITY_TYPE_CD,
                currentLookup.lookup_cd(),
                CHANGE_TYPE_CD,
                request.certified_by(),
                now,
                null,
                null,
                "Certified filter lookup " + currentLookup.lookup_cd()
        ));

        return toDto(
                certifiedLookup,
                reviewPeriodFloorDays,
                filterLookupReadDao.countValues(request.client_id(), currentLookup.lookup_cd())
        );
    }

    private FilterLookupEffectiveReviewDto toDto(SemanticFilterLookupRecord record,
                                                 int reviewPeriodFloorDays,
                                                 long valueCount) {
        Integer override = record.review_period_days_override();
        return new FilterLookupEffectiveReviewDto(
                record.id(),
                record.lookup_cd(),
                record.construction_type_cd(),
                override,
                effectiveReviewPeriodDays(override, reviewPeriodFloorDays),
                effectiveReviewPeriodSourceCode(override, reviewPeriodFloorDays),
                record.governance_status_cd(),
                record.health_status_cd(),
                valueCount,
                record.next_review_due_dt(),
                record.lifecycle_status_cd(),
                record.last_certified_ts(),
                record.last_certified_by(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private int effectiveReviewPeriodDays(Integer override, int reviewPeriodFloorDays) {
        if (override == null || override > reviewPeriodFloorDays) {
            return reviewPeriodFloorDays;
        }
        return override;
    }

    private String effectiveReviewPeriodSourceCode(Integer override, int reviewPeriodFloorDays) {
        if (override == null) {
            return GOV_DEFAULT;
        }
        if (override > reviewPeriodFloorDays) {
            return GOV_ENFORCED;
        }
        return LOOKUP_OVERRIDE;
    }

    private static final class MissingFilterLookupReadDao implements FilterLookupReadDao {

        @Override
        public java.util.List<SemanticFilterLookupRecord> findLookups(String clientId,
                                                                      String governanceStatusCode,
                                                                      String healthStatusCode,
                                                                      String lifecycleStatusCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }

        @Override
        public Optional<SemanticFilterLookupRecord> findLookup(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }

        @Override
        public java.util.List<com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord> findManualValues(String clientId,
                                                                                                              String lookupCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }

        @Override
        public long countStaleValues(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }

        @Override
        public long countValues(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }
    }

    private static final class MissingFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {

        @Override
        public SemanticFilterLookupRecord insertLookup(com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest request) {
            throw new SemanticLayerException("FilterLookupRegistrationWriteDao is not configured");
        }

        @Override
        public SemanticFilterLookupRecord certifyLookup(FilterLookupCertificationWriteRequest request) {
            throw new SemanticLayerException("FilterLookupRegistrationWriteDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord insertWorkflowTask(
                com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest request
        ) {
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
