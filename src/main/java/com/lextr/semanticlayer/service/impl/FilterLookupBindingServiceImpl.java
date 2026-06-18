package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dto.FilterLookupBindingPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.exception.FilterLookupBindingServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupBindingRecord;
import com.lextr.semanticlayer.model.FilterLookupBindingWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupBindingService;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
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
public class FilterLookupBindingServiceImpl implements FilterLookupBindingService {

    private static final String CHANGE_TYPE_CD = "BOUND";
    private static final String ENTITY_TYPE_CD = "FILTER_LOOKUP";

    private final FilterLookupReadDao filterLookupReadDao;
    private final FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao;
    private final FilterLookupPolicyClient filterLookupPolicyClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public FilterLookupBindingServiceImpl(
            ObjectProvider<FilterLookupReadDao> filterLookupReadDaoProvider,
            ObjectProvider<FilterLookupRegistrationWriteDao> filterLookupRegistrationWriteDaoProvider,
            ObjectProvider<FilterLookupPolicyClient> filterLookupPolicyClientProvider,
            @Qualifier("semanticLayerTransactionOperations")
            ObjectProvider<TransactionOperations> transactionOperationsProvider
    ) {
        this(
                filterLookupReadDaoProvider.getIfAvailable(MissingFilterLookupReadDao::new),
                filterLookupRegistrationWriteDaoProvider.getIfAvailable(MissingFilterLookupRegistrationWriteDao::new),
                filterLookupPolicyClientProvider.getIfAvailable(
                        () -> request -> new FilterLookupPolicyDecisionDto(true, null, null)
                ),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    FilterLookupBindingServiceImpl(FilterLookupReadDao filterLookupReadDao,
                                   FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao,
                                   FilterLookupPolicyClient filterLookupPolicyClient,
                                   TransactionOperations transactionOperations) {
        this.filterLookupReadDao = filterLookupReadDao;
        this.filterLookupRegistrationWriteDao = filterLookupRegistrationWriteDao;
        this.filterLookupPolicyClient = filterLookupPolicyClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public FilterLookupBindingResponseDto bindLookup(String lookupCode, FilterLookupBindingRequestDto request) {
        SemanticFilterLookupRecord currentLookup = findRequiredLookup(request.client_id(), lookupCode);

        LocalDate nextReviewDueDate = currentLookup.next_review_due_dt();
        boolean isOverdue = nextReviewDueDate != null && nextReviewDueDate.isBefore(LocalDate.now(ZoneOffset.UTC));

        validateBindingPolicy(request, lookupCode, isOverdue);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            return transactionOperations.execute(status -> persistBinding(
                    request,
                    lookupCode,
                    now
            ));
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FilterLookupBindingServiceException("Unable to bind filter lookup", exception);
        }
    }

    private SemanticFilterLookupRecord findRequiredLookup(String clientId, String lookupCode) {
        return filterLookupReadDao.findLookup(clientId, lookupCode)
                .orElseThrow(() -> new RegistryResourceNotFoundException("filter lookup", lookupCode));
    }

    private void validateBindingPolicy(FilterLookupBindingRequestDto request, String lookupCode, boolean isOverdue) {
        FilterLookupPolicyDecisionDto decision = filterLookupPolicyClient.validateBinding(
                new FilterLookupBindingPolicyRequestDto(
                        request.client_id(),
                        lookupCode,
                        request.binding_context_cd(),
                        isOverdue
                )
        );
        if (!decision.allowed()) {
            throw new PolicyViolationException(decision.code(), decision.message());
        }
    }

    private FilterLookupBindingResponseDto persistBinding(FilterLookupBindingRequestDto request,
                                                           String lookupCode,
                                                           OffsetDateTime now) {
        FilterLookupBindingRecord boundRecord = filterLookupRegistrationWriteDao.insertBinding(
                new FilterLookupBindingWriteRequest(
                        lookupCode,
                        request.bound_obj(),
                        request.bound_attr_cd(),
                        request.binding_context_cd(),
                        request.binding_ref(),
                        request.bound_by(),
                        now,
                        true
                )
        );

        filterLookupRegistrationWriteDao.insertMetadataChangeHistory(new FilterLookupMetadataChangeHistoryWriteRequest(
                ENTITY_TYPE_CD,
                lookupCode,
                CHANGE_TYPE_CD,
                request.bound_by(),
                now,
                null,
                null,
                "Bound filter lookup " + lookupCode + " to " + request.bound_obj() + "." + request.bound_attr_cd() + " (" + request.binding_context_cd() + ")"
        ));

        return toDto(boundRecord);
    }

    private FilterLookupBindingResponseDto toDto(FilterLookupBindingRecord record) {
        return new FilterLookupBindingResponseDto(
                record.id(),
                record.lookup_cd(),
                record.bound_obj(),
                record.bound_attr_cd(),
                record.binding_context_cd(),
                record.binding_ref(),
                record.bound_by(),
                record.bound_ts(),
                record.is_active_flg()
        );
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
