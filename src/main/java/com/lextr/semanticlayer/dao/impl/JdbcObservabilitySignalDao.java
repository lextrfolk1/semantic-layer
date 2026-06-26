package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.ObservabilitySignalDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.ObservabilitySignalCorrelationWriteRequest;
import com.lextr.semanticlayer.model.ObservabilitySignalRecord;
import com.lextr.semanticlayer.model.ObservabilitySignalWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcObservabilitySignalDao implements ObservabilitySignalDao {

    static final String INSERT_SIGNAL = "observability_signal.insert";
    static final String FIND_ALL = "observability_signal.find_all";
    static final String CORRELATE_SIGNAL = "observability_signal.correlate";

    private static final RowMapper<ObservabilitySignalRecord> ROW_MAPPER = JdbcObservabilitySignalDao::mapObservabilitySignalRow;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcObservabilitySignalDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                      SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public ObservabilitySignalRecord insertSignal(ObservabilitySignalWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", request.client_id())
                .addValue("signal_type_cd", request.signal_type_cd())
                .addValue("severity_cd", request.severity_cd())
                .addValue("signal_status_cd", request.signal_status_cd())
                .addValue("source_system_cd", request.source_system_cd())
                .addValue("source_entity_type_cd", request.source_entity_type_cd())
                .addValue("source_entity_ref_txt", request.source_entity_ref_txt())
                .addValue("correlation_key_txt", request.correlation_key_txt())
                .addValue("finding_summary_txt", request.finding_summary_txt())
                .addValue("finding_detail_txt", request.finding_detail_txt())
                .addValue("detected_ts", request.detected_ts())
                .addValue("dq_rerun_requested_flg", request.dq_rerun_requested_flg())
                .addValue("dq_rerun_reason_txt", request.dq_rerun_reason_txt())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        return jdbcTemplate().query(sqlQueryLoaderUtil.getQuery(INSERT_SIGNAL), parameters, ROW_MAPPER)
                .stream()
                .findFirst()
                .orElseThrow(() -> new SemanticLayerException("Insert observability signal returned no rows"));
    }

    @Override
    public List<ObservabilitySignalRecord> findSignals(String clientId,
                                                      String signalTypeCode,
                                                      String severityCode,
                                                      String signalStatusCode,
                                                      String correlationKeyText) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("signal_type_cd", signalTypeCode)
                .addValue("severity_cd", severityCode)
                .addValue("signal_status_cd", signalStatusCode)
                .addValue("correlation_key_txt", correlationKeyText);
        return jdbcTemplate().query(sqlQueryLoaderUtil.getQuery(FIND_ALL), parameters, ROW_MAPPER);
    }

    @Override
    public Optional<ObservabilitySignalRecord> correlateSignal(ObservabilitySignalCorrelationWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", request.id())
                .addValue("client_id", request.client_id())
                .addValue("signal_status_cd", request.signal_status_cd())
                .addValue("workflow_task_id", request.workflow_task_id())
                .addValue("dq_rerun_requested_flg", request.dq_rerun_requested_flg())
                .addValue("dq_rerun_reason_txt", request.dq_rerun_reason_txt())
                .addValue("acknowledged_ts", request.acknowledged_ts())
                .addValue("resolved_ts", request.resolved_ts())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        return jdbcTemplate().query(sqlQueryLoaderUtil.getQuery(CORRELATE_SIGNAL), parameters, ROW_MAPPER)
                .stream()
                .findFirst();
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static ObservabilitySignalRecord mapObservabilitySignalRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ObservabilitySignalRecord(
                getLong(resultSet, "id"),
                resultSet.getString("client_id"),
                resultSet.getString("signal_type_cd"),
                resultSet.getString("severity_cd"),
                resultSet.getString("signal_status_cd"),
                resultSet.getString("source_system_cd"),
                resultSet.getString("source_entity_type_cd"),
                resultSet.getString("source_entity_ref_txt"),
                resultSet.getString("correlation_key_txt"),
                resultSet.getString("finding_summary_txt"),
                resultSet.getString("finding_detail_txt"),
                getOffsetDateTime(resultSet, "detected_ts"),
                getOffsetDateTime(resultSet, "acknowledged_ts"),
                getOffsetDateTime(resultSet, "resolved_ts"),
                getLongOrNull(resultSet, "workflow_task_id"),
                resultSet.getBoolean("dq_rerun_requested_flg"),
                resultSet.getString("dq_rerun_reason_txt"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    private static Long getLongOrNull(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, Long.class);
    }

    private static Long getLong(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, Long.class);
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }
}
