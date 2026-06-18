package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupEffectiveReviewReadDao;
import com.lextr.semanticlayer.dao.FilterLookupExecutionLogWriteDao;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.exception.FilterLookupPreviewServiceException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogRecord;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.FilterLookupValueCountRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class FilterLookupPreviewServiceImpl implements FilterLookupPreviewService {

    private static final String CONSTRUCTION_TYPE_MANUAL_LIST = "MANUAL_LIST";
    private static final String RESULT_STATUS_SUCCESS = "SUCCESS";
    private static final String RESULT_STATUS_ERROR = "ERROR";

    private final FilterLookupEffectiveReviewReadDao filterLookupEffectiveReviewReadDao;
    private final FilterLookupExecutionLogWriteDao filterLookupExecutionLogWriteDao;

    @Autowired
    public FilterLookupPreviewServiceImpl(
            ObjectProvider<FilterLookupEffectiveReviewReadDao> filterLookupEffectiveReviewReadDaoProvider,
            ObjectProvider<FilterLookupExecutionLogWriteDao> filterLookupExecutionLogWriteDaoProvider
    ) {
        this(
                filterLookupEffectiveReviewReadDaoProvider.getIfAvailable(MissingFilterLookupEffectiveReviewReadDao::new),
                filterLookupExecutionLogWriteDaoProvider.getIfAvailable(MissingFilterLookupExecutionLogWriteDao::new)
        );
    }

    FilterLookupPreviewServiceImpl(FilterLookupEffectiveReviewReadDao filterLookupEffectiveReviewReadDao,
                                   FilterLookupExecutionLogWriteDao filterLookupExecutionLogWriteDao) {
        this.filterLookupEffectiveReviewReadDao = filterLookupEffectiveReviewReadDao;
        this.filterLookupExecutionLogWriteDao = filterLookupExecutionLogWriteDao;
    }

    @Override
    public FilterLookupPreviewResponseDto previewFilterLookup(FilterLookupPreviewRequestDto request) {
        SemanticFilterLookupRecord lookup = filterLookupEffectiveReviewReadDao.findLookupByCode(
                        request.client_id(),
                        request.lookup_cd()
                )
                .orElseThrow(() -> new RegistryResourceNotFoundException("filter lookup", request.lookup_cd()));

        OffsetDateTime executedTs = OffsetDateTime.now(ZoneOffset.UTC);
        long startedAt = System.nanoTime();

        try {
            if (!CONSTRUCTION_TYPE_MANUAL_LIST.equals(lookup.construction_type_cd())) {
                throw logAndBuildUnsupportedConstructionTypeException(request, lookup, executedTs, startedAt);
            }

            List<FilterLookupPreviewValueRecord> previewValues = filterLookupEffectiveReviewReadDao.findManualValuesByLookup(
                    request.client_id(),
                    request.lookup_cd()
            );
            FilterLookupValueCountRecord valueCount = filterLookupEffectiveReviewReadDao.countValuesByLookup(
                    request.client_id(),
                    request.lookup_cd()
            );

            FilterLookupExecutionLogRecord executionLog = filterLookupExecutionLogWriteDao.insertExecutionLog(
                    new FilterLookupExecutionLogWriteRequest(
                            lookup.lookup_cd(),
                            request.executed_by(),
                            executedTs,
                            durationMs(startedAt),
                            Math.toIntExact(valueCount.value_count()),
                            false,
                            lookup.execution_strategy_cd(),
                            null,
                            RESULT_STATUS_SUCCESS,
                            null,
                            null
                    )
            );

            return new FilterLookupPreviewResponseDto(
                    executionLog.id(),
                    lookup.lookup_cd(),
                    lookup.construction_type_cd(),
                    lookup.client_id(),
                    executionLog.execution_strategy_used_cd(),
                    executionLog.result_status_cd(),
                    executionLog.executed_ts(),
                    executionLog.phase1_duration_ms(),
                    executionLog.phase1_row_count(),
                    executionLog.phase1_cache_hit_flg(),
                    valueCount.value_count(),
                    previewValues.stream().map(this::toPreviewValueDto).toList()
            );
        } catch (RegistryResourceNotFoundException exception) {
            throw exception;
        } catch (FilterLookupPreviewServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            logFailure(
                    lookup.lookup_cd(),
                    request.executed_by(),
                    executedTs,
                    durationMs(startedAt),
                    lookup.execution_strategy_cd(),
                    exception.getMessage()
            );
            throw new FilterLookupPreviewServiceException(
                    "Unable to preview filter lookup " + request.lookup_cd(),
                    exception
            );
        }
    }

    private FilterLookupPreviewServiceException logAndBuildUnsupportedConstructionTypeException(
            FilterLookupPreviewRequestDto request,
            SemanticFilterLookupRecord lookup,
            OffsetDateTime executedTs,
            long startedAt
    ) {
        String message = "Preview execution is not configured for construction type " + lookup.construction_type_cd();
        logFailure(
                lookup.lookup_cd(),
                request.executed_by(),
                executedTs,
                durationMs(startedAt),
                lookup.execution_strategy_cd(),
                message
        );
        return new FilterLookupPreviewServiceException(message);
    }

    private void logFailure(String lookupCode,
                            String executedBy,
                            OffsetDateTime executedTs,
                            Integer durationMs,
                            String executionStrategyUsedCode,
                            String errorMessage) {
        try {
            filterLookupExecutionLogWriteDao.insertExecutionLog(new FilterLookupExecutionLogWriteRequest(
                    lookupCode,
                    executedBy,
                    executedTs,
                    durationMs,
                    0,
                    false,
                    executionStrategyUsedCode,
                    null,
                    RESULT_STATUS_ERROR,
                    errorMessage,
                    null
            ));
        } catch (RuntimeException ignored) {
            // Keep the original failure path intact if logging also fails.
        }
    }

    private Integer durationMs(long startedAt) {
        return Math.toIntExact((System.nanoTime() - startedAt) / 1_000_000L);
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

    private static final class MissingFilterLookupEffectiveReviewReadDao implements FilterLookupEffectiveReviewReadDao {

        @Override
        public Optional<SemanticFilterLookupRecord> findLookupByCode(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupEffectiveReviewReadDao is not configured");
        }

        @Override
        public List<FilterLookupPreviewValueRecord> findManualValuesByLookup(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupEffectiveReviewReadDao is not configured");
        }

        @Override
        public FilterLookupValueCountRecord countValuesByLookup(String clientId, String lookupCode) {
            throw new SemanticLayerException("FilterLookupEffectiveReviewReadDao is not configured");
        }
    }

    private static final class MissingFilterLookupExecutionLogWriteDao implements FilterLookupExecutionLogWriteDao {

        @Override
        public FilterLookupExecutionLogRecord insertExecutionLog(FilterLookupExecutionLogWriteRequest request) {
            throw new SemanticLayerException("FilterLookupExecutionLogWriteDao is not configured");
        }
    }
}
