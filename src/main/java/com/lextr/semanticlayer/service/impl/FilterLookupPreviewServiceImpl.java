package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupExecutionLogWriteDao;
import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.exception.FilterLookupPreviewServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
import com.lextr.semanticlayer.service.FilterLookupSqlPreviewClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class FilterLookupPreviewServiceImpl implements FilterLookupPreviewService {

    private static final Logger logger = LoggerFactory.getLogger(FilterLookupPreviewServiceImpl.class);

    private static final String MANUAL_LIST = "MANUAL_LIST";
    private static final String SQL_QUERY = "SQL_QUERY";
    private static final String SUCCESS = "SUCCESS";
    private static final String BLOCKED = "BLOCKED";

    private final FilterLookupReadDao filterLookupReadDao;
    private final FilterLookupExecutionLogWriteDao filterLookupExecutionLogWriteDao;
    private final FilterLookupPolicyClient filterLookupPolicyClient;
    private final FilterLookupSqlPreviewClient filterLookupSqlPreviewClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public FilterLookupPreviewServiceImpl(
            ObjectProvider<FilterLookupReadDao> filterLookupReadDaoProvider,
            ObjectProvider<FilterLookupExecutionLogWriteDao> filterLookupExecutionLogWriteDaoProvider,
            ObjectProvider<FilterLookupPolicyClient> filterLookupPolicyClientProvider,
            ObjectProvider<FilterLookupSqlPreviewClient> filterLookupSqlPreviewClientProvider,
            @Qualifier("semanticLayerTransactionOperations")
            ObjectProvider<TransactionOperations> transactionOperationsProvider
    ) {
        this(
                filterLookupReadDaoProvider.getIfAvailable(MissingFilterLookupReadDao::new),
                filterLookupExecutionLogWriteDaoProvider.getIfAvailable(MissingFilterLookupExecutionLogWriteDao::new),
                filterLookupPolicyClientProvider.getIfAvailable(() -> request -> new FilterLookupPolicyDecisionDto(true, null, null)),
                filterLookupSqlPreviewClientProvider.getIfAvailable(MissingFilterLookupSqlPreviewClient::new),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    FilterLookupPreviewServiceImpl(FilterLookupReadDao filterLookupReadDao,
                                   FilterLookupExecutionLogWriteDao filterLookupExecutionLogWriteDao,
                                   FilterLookupPolicyClient filterLookupPolicyClient,
                                   FilterLookupSqlPreviewClient filterLookupSqlPreviewClient,
                                   TransactionOperations transactionOperations) {
        this.filterLookupReadDao = filterLookupReadDao;
        this.filterLookupExecutionLogWriteDao = filterLookupExecutionLogWriteDao;
        this.filterLookupPolicyClient = filterLookupPolicyClient;
        this.filterLookupSqlPreviewClient = filterLookupSqlPreviewClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public List<FilterLookupPreviewResponseDto> previewLookups(FilterLookupPreviewRequestDto request) {
        logger.debug("Previewing filter lookups. clientId={}, executedBy={}, lookupCodeCount={}",
                request.client_id(), request.executed_by(), request.lookup_codes().size());
        try {
            List<SemanticFilterLookupRecord> lookups = request.lookup_codes().stream()
                    .map(lookupCode -> findRequiredLookup(request.client_id(), lookupCode))
                    .toList();

            validatePreviewPolicies(request, lookups);

            List<FilterLookupPreviewResponseDto> responses = transactionOperations.execute(status -> lookups.stream()
                    .map(lookup -> previewLookup(request, lookup))
                    .toList());
            logger.debug("Filter lookup preview completed. clientId={}, resultCount={}", request.client_id(), responses.size());
            return responses;
        } catch (PolicyViolationException exception) {
            logger.warn("Filter lookup preview denied. clientId={}, errorMessage={}", request.client_id(), exception.getMessage(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            logger.error("Filter lookup preview failed. clientId={}, errorMessage={}", request.client_id(), exception.getMessage(), exception);
            throw new FilterLookupPreviewServiceException("Unable to preview filter lookups", exception);
        }
    }

    private SemanticFilterLookupRecord findRequiredLookup(String clientId, String lookupCode) {
        return filterLookupReadDao.findLookup(clientId, lookupCode)
                .orElseThrow(() -> new RegistryResourceNotFoundException("filter lookup", lookupCode));
    }

    private void validatePreviewPolicies(FilterLookupPreviewRequestDto request, List<SemanticFilterLookupRecord> lookups) {
        for (SemanticFilterLookupRecord lookup : lookups) {
            FilterLookupPolicyDecisionDto decision = filterLookupPolicyClient.validatePreviewExecution(
                    new FilterLookupPreviewPolicyRequestDto(
                            request.client_id(),
                            request.executed_by(),
                            lookup.lookup_cd(),
                            lookup.construction_type_cd(),
                            lookup.execution_strategy_cd()
                    )
            );
            if (!decision.allowed()) {
                logger.warn("Filter lookup preview policy denied. clientId={}, lookupCode={}, constructionTypeCode={}, executionStrategyCode={}, policyCode={}",
                        request.client_id(), lookup.lookup_cd(), lookup.construction_type_cd(), lookup.execution_strategy_cd(), decision.code());
                recordBlockedExecution(request.executed_by(), lookup, decision.code());
                throw new PolicyViolationException(decision.code(), decision.message());
            }
        }
    }

    private void recordBlockedExecution(String executedBy,
                                        SemanticFilterLookupRecord lookup,
                                        String blockedByPolicyCode) {
        filterLookupExecutionLogWriteDao.insertExecutionLog(new FilterLookupExecutionLogWriteRequest(
                lookup.lookup_cd(),
                executedBy,
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                null,
                false,
                lookup.execution_strategy_cd(),
                null,
                BLOCKED,
                null,
                blockedByPolicyCode
        ));
    }

    private FilterLookupPreviewResponseDto previewLookup(FilterLookupPreviewRequestDto request,
                                                         SemanticFilterLookupRecord lookup) {
        long startedNanos = System.nanoTime();
        logger.debug("Resolving filter lookup preview. clientId={}, lookupCode={}, constructionTypeCode={}",
                request.client_id(), lookup.lookup_cd(), lookup.construction_type_cd());
        PreviewResolution resolution = resolvePreview(request.client_id(), lookup);
        int durationMillis = elapsedMillis(startedNanos);

        filterLookupExecutionLogWriteDao.insertExecutionLog(new FilterLookupExecutionLogWriteRequest(
                lookup.lookup_cd(),
                request.executed_by(),
                OffsetDateTime.now(ZoneOffset.UTC),
                durationMillis,
                resolution.phase1RowCount(),
                resolution.phase1CacheHitFlag(),
                resolution.executionStrategyUsedCode(),
                null,
                SUCCESS,
                null,
                null
        ));

        FilterLookupPreviewResponseDto response = new FilterLookupPreviewResponseDto(
                lookup.lookup_cd(),
                lookup.construction_type_cd(),
                resolution.executionStrategyUsedCode(),
                resolution.phase1RowCount(),
                resolution.phase1CacheHitFlag(),
                SUCCESS,
                resolution.values()
        );
        logger.debug("Filter lookup preview resolved. clientId={}, lookupCode={}, rowCount={}, durationMillis={}",
                request.client_id(), lookup.lookup_cd(), response.phase1_row_count(), durationMillis);
        return response;
    }

    private PreviewResolution resolvePreview(String clientId, SemanticFilterLookupRecord lookup) {
        return switch (lookup.construction_type_cd()) {
            case MANUAL_LIST -> manualPreview(clientId, lookup);
            case SQL_QUERY -> sqlPreview(clientId, lookup);
            default -> throw new FilterLookupPreviewServiceException(
                    "Unsupported filter lookup construction type " + lookup.construction_type_cd()
            );
        };
    }

    private PreviewResolution manualPreview(String clientId, SemanticFilterLookupRecord lookup) {
        List<FilterLookupPreviewValueDto> values = filterLookupReadDao.findManualValues(clientId, lookup.lookup_cd()).stream()
                .map(this::toPreviewValueDto)
                .toList();
        logger.debug("Manual filter lookup preview resolved. clientId={}, lookupCode={}, rowCount={}",
                clientId, lookup.lookup_cd(), values.size());
        return new PreviewResolution(values, values.size(), false, lookup.execution_strategy_cd());
    }

    private PreviewResolution sqlPreview(String clientId, SemanticFilterLookupRecord lookup) {
        List<FilterLookupPreviewValueDto> values = filterLookupSqlPreviewClient.previewDistinctValues(clientId, lookup);
        logger.debug("SQL filter lookup preview resolved. clientId={}, lookupCode={}, rowCount={}",
                clientId, lookup.lookup_cd(), values.size());
        return new PreviewResolution(values, values.size(), false, lookup.execution_strategy_cd());
    }

    private FilterLookupPreviewValueDto toPreviewValueDto(FilterLookupPreviewValueRecord record) {
        return new FilterLookupPreviewValueDto(
                record.value_cd(),
                record.value_desc(),
                record.lifecycle_status_cd(),
                record.validated_flg(),
                record.anticipated_dt(),
                record.workflow_ref(),
                record.last_seen_in_source_ts(),
                record.auto_expire_after_days(),
                record.alert_txt(),
                record.added_by(),
                record.added_ts(),
                record.certified_by(),
                record.certified_ts(),
                record.updated_ts()
        );
    }

    private int elapsedMillis(long startedNanos) {
        long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        return (int) Math.min(Integer.MAX_VALUE, durationMillis);
    }

    private record PreviewResolution(List<FilterLookupPreviewValueDto> values,
                                     Integer phase1RowCount,
                                     boolean phase1CacheHitFlag,
                                     String executionStrategyUsedCode) {
    }

    private static final class MissingFilterLookupReadDao implements FilterLookupReadDao {

        @Override
        public List<SemanticFilterLookupRecord> findLookups(String clientId,
                                                            String governanceStatusCode,
                                                            String healthStatusCode,
                                                            String lifecycleStatusCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }

        @Override
        public java.util.Optional<SemanticFilterLookupRecord> findLookup(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }

        @Override
        public List<FilterLookupPreviewValueRecord> findManualValues(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }

        @Override
        public long countValues(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupReadDao is not configured");
        }
    }

    private static final class MissingFilterLookupExecutionLogWriteDao implements FilterLookupExecutionLogWriteDao {

        @Override
        public com.lextr.semanticlayer.model.FilterLookupExecutionLogRecord insertExecutionLog(
                FilterLookupExecutionLogWriteRequest request
        ) {
            throw new SemanticLayerException("FilterLookupExecutionLogWriteDao is not configured");
        }
    }

    private static final class MissingFilterLookupSqlPreviewClient implements FilterLookupSqlPreviewClient {

        @Override
        public List<FilterLookupPreviewValueDto> previewDistinctValues(String clientId, SemanticFilterLookupRecord lookup) {
            throw new FilterLookupPreviewServiceException("FilterLookupSqlPreviewClient is not configured");
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
