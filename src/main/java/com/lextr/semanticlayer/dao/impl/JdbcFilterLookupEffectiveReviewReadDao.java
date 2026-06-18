package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.FilterLookupEffectiveReviewReadDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.FilterLookupValueCountRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFilterLookupEffectiveReviewReadDao implements FilterLookupEffectiveReviewReadDao {

    static final String FIND_LOOKUP_BY_CODE = "filter_lookup_effective_review.find_lookup_by_code";
    static final String FIND_MANUAL_VALUES_BY_LOOKUP = "filter_lookup_effective_review.find_manual_values_by_lookup";
    static final String COUNT_VALUES_BY_LOOKUP = "filter_lookup_effective_review.count_values_by_lookup";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcFilterLookupEffectiveReviewReadDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                                  SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public Optional<SemanticFilterLookupRecord> findLookupByCode(String clientId, String lookupCode) {
        MapSqlParameterSource parameters = baseParameters(clientId, lookupCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_LOOKUP_BY_CODE),
                parameters,
                semanticFilterLookupRowMapper()
        ).stream().findFirst();
    }

    @Override
    public List<FilterLookupPreviewValueRecord> findManualValuesByLookup(String clientId, String lookupCode) {
        MapSqlParameterSource parameters = baseParameters(clientId, lookupCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_MANUAL_VALUES_BY_LOOKUP),
                parameters,
                previewValueRowMapper()
        );
    }

    @Override
    public FilterLookupValueCountRecord countValuesByLookup(String clientId, String lookupCode) {
        MapSqlParameterSource parameters = baseParameters(clientId, lookupCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(COUNT_VALUES_BY_LOOKUP),
                parameters,
                valueCountRowMapper()
        ).stream().findFirst().orElse(new FilterLookupValueCountRecord(lookupCode, clientId, 0L));
    }

    private MapSqlParameterSource baseParameters(String clientId, String lookupCode) {
        return new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lookup_cd", lookupCode);
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    static RowMapper<SemanticFilterLookupRecord> semanticFilterLookupRowMapper() {
        return (resultSet, rowNum) -> new SemanticFilterLookupRecord(
                getLong(resultSet, "id"),
                resultSet.getString("lookup_cd"),
                resultSet.getString("construction_type_cd"),
                resultSet.getString("manual_subtype_cd"),
                resultSet.getString("filter_obj"),
                resultSet.getString("filter_condition_txt"),
                resultSet.getString("filter_attr_cd"),
                resultSet.getString("validation_obj"),
                resultSet.getString("validation_attr_cd"),
                resultSet.getString("suggested_target_attr_cd"),
                resultSet.getString("execution_strategy_cd"),
                getInteger(resultSet, "max_input_set_size"),
                getInteger(resultSet, "max_output_rows"),
                getInteger(resultSet, "cache_ttl_min"),
                getInteger(resultSet, "review_period_days_override"),
                resultSet.getBoolean("rules_eligible_flg"),
                resultSet.getBoolean("qs_eligible_flg"),
                resultSet.getBoolean("ai_eligible_flg"),
                resultSet.getBoolean("replicate_to_ch_flg"),
                resultSet.getString("description_txt"),
                resultSet.getString("client_id"),
                resultSet.getString("governance_status_cd"),
                resultSet.getString("health_status_cd"),
                getOffsetDateTime(resultSet, "last_certified_ts"),
                resultSet.getString("last_certified_by"),
                getLocalDate(resultSet, "next_review_due_dt"),
                resultSet.getString("lifecycle_status_cd"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    static RowMapper<FilterLookupPreviewValueRecord> previewValueRowMapper() {
        return (resultSet, rowNum) -> new FilterLookupPreviewValueRecord(
                resultSet.getString("lookup_cd"),
                resultSet.getString("client_id"),
                resultSet.getString("value_cd"),
                resultSet.getString("value_desc"),
                resultSet.getString("lifecycle_status_cd"),
                resultSet.getBoolean("validated_flg"),
                getLocalDate(resultSet, "anticipated_dt"),
                resultSet.getString("workflow_ref"),
                getOffsetDateTime(resultSet, "last_seen_in_source_ts"),
                getInteger(resultSet, "auto_expire_after_days"),
                resultSet.getString("alert_txt"),
                resultSet.getString("added_by"),
                getOffsetDateTime(resultSet, "added_ts"),
                resultSet.getString("certified_by"),
                getOffsetDateTime(resultSet, "certified_ts"),
                getOffsetDateTime(resultSet, "updated_ts")
        );
    }

    static RowMapper<FilterLookupValueCountRecord> valueCountRowMapper() {
        return (resultSet, rowNum) -> new FilterLookupValueCountRecord(
                resultSet.getString("lookup_cd"),
                resultSet.getString("client_id"),
                getLong(resultSet, "value_count")
        );
    }

    private static Long getLong(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer getInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).intValue();
    }

    private static LocalDate getLocalDate(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, LocalDate.class);
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }
}
