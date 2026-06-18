package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupSqlPreviewClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnMissingBean(FilterLookupSqlPreviewClient.class)
public class JdbcFilterLookupSqlPreviewClient implements FilterLookupSqlPreviewClient {

    private final FilterLookupReadDao filterLookupReadDao;

    public JdbcFilterLookupSqlPreviewClient(FilterLookupReadDao filterLookupReadDao) {
        this.filterLookupReadDao = filterLookupReadDao;
    }

    @Override
    public List<FilterLookupPreviewValueDto> previewDistinctValues(String clientId, SemanticFilterLookupRecord lookup) {
        return filterLookupReadDao.findSqlValues(clientId, lookup).stream()
                .map(this::toDto)
                .toList();
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
