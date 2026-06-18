package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.FilterLookupExecutionLogWriteDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogRecord;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

@Repository
public class JdbcFilterLookupExecutionLogWriteDao implements FilterLookupExecutionLogWriteDao {

    static final String INSERT_EXECUTION = "filter_lookup_exec_log.insert_execution";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcFilterLookupExecutionLogWriteDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                                SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public FilterLookupExecutionLogRecord insertExecutionLog(FilterLookupExecutionLogWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("lookup_cd", request.lookup_cd())
                .addValue("executed_by", request.executed_by())
                .addValue("executed_ts", request.executed_ts())
                .addValue("phase1_duration_ms", request.phase1_duration_ms())
                .addValue("phase1_row_count", request.phase1_row_count())
                .addValue("phase1_cache_hit_flg", request.phase1_cache_hit_flg())
                .addValue("execution_strategy_used_cd", request.execution_strategy_used_cd())
                .addValue("phase2_duration_ms", request.phase2_duration_ms())
                .addValue("result_status_cd", request.result_status_cd())
                .addValue("error_txt", request.error_txt())
                .addValue("blocked_by_policy_cd", request.blocked_by_policy_cd());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_EXECUTION),
                parameters,
                (resultSet, rowNum) -> new FilterLookupExecutionLogRecord(
                        getLong(resultSet, "id"),
                        resultSet.getString("lookup_cd"),
                        resultSet.getString("executed_by"),
                        getOffsetDateTime(resultSet, "executed_ts"),
                        getInteger(resultSet, "phase1_duration_ms"),
                        getInteger(resultSet, "phase1_row_count"),
                        resultSet.getBoolean("phase1_cache_hit_flg"),
                        resultSet.getString("execution_strategy_used_cd"),
                        getInteger(resultSet, "phase2_duration_ms"),
                        resultSet.getString("result_status_cd"),
                        resultSet.getString("error_txt"),
                        resultSet.getString("blocked_by_policy_cd")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert filter lookup execution log returned no rows"));
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static Integer getInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long getLong(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).longValue();
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }
}
