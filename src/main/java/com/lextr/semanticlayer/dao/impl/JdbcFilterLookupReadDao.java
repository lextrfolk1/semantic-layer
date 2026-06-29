package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.regex.Pattern;

@Repository
public class JdbcFilterLookupReadDao implements FilterLookupReadDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcFilterLookupReadDao.class);

    static final String FIND_ALL = "filter_lookup_effective_review.find_all";
    static final String FIND_BY_CODE = "filter_lookup_effective_review.find_lookup_by_code";
    static final String FIND_MANUAL_VALUES_BY_LOOKUP = "filter_lookup_effective_review.find_manual_values_by_lookup";
    static final String FIND_SQL_VALUES_TEMPLATE = "filter_lookup_effective_review.find_sql_values_template";
    static final String COUNT_VALUES_BY_LOOKUP = "filter_lookup_effective_review.count_values_by_lookup";
    static final String COUNT_STALE_VALUES_BY_LOOKUP = "filter_lookup_effective_review.count_stale_values_by_lookup";
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int DEFAULT_MAX_OUTPUT_ROWS = 10_000;

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcFilterLookupReadDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                   SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<SemanticFilterLookupRecord> findLookups(String clientId,
                                                        String governanceStatusCode,
                                                        String healthStatusCode,
                                                        String lifecycleStatusCode) {
        logger.debug("Executing filter lookup list query. clientId={}, governanceStatusCode={}, healthStatusCode={}, lifecycleStatusCode={}",
                clientId, governanceStatusCode, healthStatusCode, lifecycleStatusCode);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("governance_status_cd", governanceStatusCode)
                .addValue("health_status_cd", healthStatusCode)
                .addValue("lifecycle_status_cd", lifecycleStatusCode);
        List<SemanticFilterLookupRecord> records = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_ALL),
                parameters,
                filterLookupRowMapper()
        );
        logger.debug("Filter lookup list query completed. clientId={}, resultCount={}", clientId, records.size());
        return records;
    }

    @Override
    public Optional<SemanticFilterLookupRecord> findLookup(String clientId, String lookupCode) {
        logger.debug("Executing filter lookup query by code. clientId={}, lookupCode={}", clientId, lookupCode);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lookup_cd", lookupCode);
        Optional<SemanticFilterLookupRecord> record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_BY_CODE),
                parameters,
                filterLookupRowMapper()
        ).stream().findFirst();
        logger.debug("Filter lookup query by code completed. clientId={}, lookupCode={}, found={}",
                clientId, lookupCode, record.isPresent());
        return record;
    }

    @Override
    public List<FilterLookupPreviewValueRecord> findManualValues(String clientId, String lookupCode) {
        logger.debug("Executing manual filter lookup value query. clientId={}, lookupCode={}", clientId, lookupCode);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lookup_cd", lookupCode);
        List<FilterLookupPreviewValueRecord> records = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_MANUAL_VALUES_BY_LOOKUP),
                parameters,
                previewValueRowMapper()
        );
        logger.debug("Manual filter lookup value query completed. clientId={}, lookupCode={}, resultCount={}",
                clientId, lookupCode, records.size());
        return records;
    }

    @Override
    public List<FilterLookupPreviewValueRecord> findSqlValues(String clientId, SemanticFilterLookupRecord lookup) {
        logger.debug("Executing SQL filter lookup value query. clientId={}, lookupCode={}, maxOutputRows={}",
                clientId, lookup.lookup_cd(), lookup.max_output_rows() == null ? DEFAULT_MAX_OUTPUT_ROWS : lookup.max_output_rows());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("lookup_cd", lookup.lookup_cd())
                .addValue("client_id", clientId)
                .addValue("max_output_rows", lookup.max_output_rows() == null ? DEFAULT_MAX_OUTPUT_ROWS : lookup.max_output_rows());
        List<FilterLookupPreviewValueRecord> records = jdbcTemplate().query(
                sqlPreviewQuery(lookup),
                parameters,
                previewValueRowMapper()
        );
        logger.debug("SQL filter lookup value query completed. clientId={}, lookupCode={}, resultCount={}",
                clientId, lookup.lookup_cd(), records.size());
        return records;
    }

    @Override
    public long countValues(String clientId, String lookupCode) {
        logger.debug("Executing filter lookup value count query. clientId={}, lookupCode={}", clientId, lookupCode);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lookup_cd", lookupCode);
        long count = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(COUNT_VALUES_BY_LOOKUP),
                parameters,
                (resultSet, rowNum) -> getLong(resultSet, "value_count")
        ).stream().findFirst().orElse(0L);
        logger.debug("Filter lookup value count query completed. clientId={}, lookupCode={}, valueCount={}",
                clientId, lookupCode, count);
        return count;
    }

    @Override
    public long countStaleValues(String clientId, String lookupCode) {
        logger.debug("Executing stale filter lookup value count query. clientId={}, lookupCode={}", clientId, lookupCode);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lookup_cd", lookupCode);
        long count = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(COUNT_STALE_VALUES_BY_LOOKUP),
                parameters,
                (resultSet, rowNum) -> getLong(resultSet, "stale_value_count")
        ).stream().findFirst().orElse(0L);
        logger.debug("Stale filter lookup value count query completed. clientId={}, lookupCode={}, staleValueCount={}",
                clientId, lookupCode, count);
        return count;
    }

    private String sqlPreviewQuery(SemanticFilterLookupRecord lookup) {
        String template = sqlQueryLoaderUtil.getQuery(FIND_SQL_VALUES_TEMPLATE);
        return String.format(
                template,
                requiredIdentifier(lookup.filter_attr_cd(), "filter_attr_cd"),
                requiredQualifiedName(lookup.filter_obj(), "filter_obj"),
                safeCondition(lookup.filter_condition_txt())
        );
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            logger.error("NamedParameterJdbcTemplate is not configured for filter lookup read DAO.");
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private String requiredIdentifier(String candidate, String fieldName) {
        if (candidate == null || candidate.isBlank() || !SQL_IDENTIFIER.matcher(candidate).matches()) {
            logger.warn("Invalid SQL identifier detected for filter lookup query. fieldName={}", fieldName);
            throw new SemanticLayerException("Invalid SQL identifier for " + fieldName);
        }
        return candidate;
    }

    private String requiredQualifiedName(String candidate, String fieldName) {
        if (candidate == null || candidate.isBlank()) {
            logger.warn("Invalid qualified SQL name detected for filter lookup query. fieldName={}", fieldName);
            throw new SemanticLayerException("Invalid qualified SQL name for " + fieldName);
        }
        String[] parts = candidate.split("\\.");
        if (parts.length == 0 || parts.length > 2) {
            logger.warn("Invalid qualified SQL name segment count detected for filter lookup query. fieldName={}", fieldName);
            throw new SemanticLayerException("Invalid qualified SQL name for " + fieldName);
        }
        for (String part : parts) {
            if (!SQL_IDENTIFIER.matcher(part).matches()) {
                logger.warn("Invalid qualified SQL name token detected for filter lookup query. fieldName={}", fieldName);
                throw new SemanticLayerException("Invalid qualified SQL name for " + fieldName);
            }
        }
        return String.join(".", parts);
    }

    private String safeCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            return "TRUE";
        }
        if (condition.contains(";") || condition.contains("--") || condition.contains("/*") || condition.contains("*/")) {
            logger.warn("Unsafe SQL filter condition detected for filter lookup query.");
            throw new SemanticLayerException("Unsafe SQL filter condition");
        }
        return condition;
    }

    static RowMapper<SemanticFilterLookupRecord> filterLookupRowMapper() {
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

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }

    private static LocalDate getLocalDate(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, LocalDate.class);
    }

    private static Integer getInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long getLong(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).longValue();
    }
}
