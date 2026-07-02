package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupSqlPreviewClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class JdbcFilterLookupSqlPreviewClient implements FilterLookupSqlPreviewClient {

    private static final Logger logger = LoggerFactory.getLogger(JdbcFilterLookupSqlPreviewClient.class);

    private final FilterLookupReadDao filterLookupReadDao;

    public JdbcFilterLookupSqlPreviewClient(FilterLookupReadDao filterLookupReadDao) {
        this.filterLookupReadDao = filterLookupReadDao;
    }

    @Override
    public List<FilterLookupPreviewValueDto> previewDistinctValues(String clientId, SemanticFilterLookupRecord lookup) {
        logger.debug("Previewing SQL filter lookup values. clientId={}, lookupCode={}, executionStrategyCode={}",
                clientId, lookup.lookup_cd(), lookup.execution_strategy_cd());
        List<FilterLookupPreviewValueDto> values = filterLookupReadDao.findSqlValues(clientId, lookup).stream()
                .map(this::toDto)
                .toList();
        logger.debug("SQL filter lookup values resolved. clientId={}, lookupCode={}, resultCount={}",
                clientId, lookup.lookup_cd(), values.size());
        return values;
    }

    private FilterLookupPreviewValueDto toDto(FilterLookupPreviewValueRecord record) {
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
}
