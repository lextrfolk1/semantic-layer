package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.AttributePairingResolutionDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributePairingCacheHitWriteRequest;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class JdbcAttributePairingResolutionDao implements AttributePairingResolutionDao {

    static final String FIND_PAIRING_BY_CODE = "attribute_pairing_resolution.find_pairing_by_code";
    static final String FIND_ACTIVE_PAIRING = "attribute_pairing_resolution.find_active_pairing_by_display_attribute";
    static final String CHECK_FILTER_ATTRIBUTE_INDEX = "attribute_pairing_resolution.check_filter_attribute_index";
    static final String FIND_CACHED_VALUE = "attribute_pairing_cache.find_value";
    static final String UPSERT_CACHED_VALUE = "attribute_pairing_cache.upsert_value";
    static final String RECORD_CACHE_HIT = "attribute_pairing_cache.record_hit";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcAttributePairingResolutionDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                             SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public Optional<AttributePairingCatalogRecord> findPairing(String clientId, String pairingCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("pairing_cd", pairingCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_PAIRING_BY_CODE),
                parameters,
                JdbcAttributePairingResolutionDao::mapPairingRow
        ).stream().findFirst();
    }

    @Override
    public Optional<AttributePairingCatalogRecord> findActivePairing(String clientId,
                                                                     String schemaCode,
                                                                     String objectCode,
                                                                     String displayAttributeCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("schema_cd", schemaCode)
                .addValue("object_cd", objectCode)
                .addValue("display_attribute_cd", displayAttributeCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_ACTIVE_PAIRING),
                parameters,
                JdbcAttributePairingResolutionDao::mapPairingRow
        ).stream().findFirst();
    }

    @Override
    public boolean isAttributeIndexed(String schemaCode, String objectCode, String attributeCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("schema_cd", schemaCode)
                .addValue("object_cd", objectCode)
                .addValue("attribute_cd", attributeCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(CHECK_FILTER_ATTRIBUTE_INDEX),
                parameters,
                (resultSet, rowNum) -> resultSet.getBoolean("indexed_flg")
        ).stream().findFirst().orElse(false);
    }

    @Override
    public Optional<AttributePairingValueCacheRecord> findCachedValue(String pairingCode,
                                                                      String clientId,
                                                                      String displayValue,
                                                                      OffsetDateTime asOfTimestamp) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("pairing_cd", pairingCode)
                .addValue("client_id", clientId)
                .addValue("display_value_txt", displayValue)
                .addValue("as_of_ts", asOfTimestamp);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_CACHED_VALUE),
                parameters,
                JdbcAttributePairingResolutionDao::mapCacheRow
        ).stream().findFirst();
    }

    @Override
    public AttributePairingValueCacheRecord upsertCachedValue(AttributePairingValueCacheWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("pairing_cd", request.pairing_cd())
                .addValue("client_id", request.client_id())
                .addValue("display_value_txt", request.display_value_txt())
                .addValue("filter_value_txt", request.filter_value_txt())
                .addValue("is_one_to_many_flg", request.is_one_to_many_flg())
                .addValue("hit_count_nbr", request.hit_count_nbr())
                .addValue("last_hit_ts", request.last_hit_ts())
                .addValue("cached_ts", request.cached_ts())
                .addValue("expires_ts", request.expires_ts());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(UPSERT_CACHED_VALUE),
                parameters,
                JdbcAttributePairingResolutionDao::mapCacheRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Upsert attribute pairing cache returned no rows"));
    }

    @Override
    public AttributePairingValueCacheRecord recordCacheHit(AttributePairingCacheHitWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("pairing_cd", request.pairing_cd())
                .addValue("display_value_txt", request.display_value_txt())
                .addValue("client_id", request.client_id())
                .addValue("last_hit_ts", request.last_hit_ts());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(RECORD_CACHE_HIT),
                parameters,
                JdbcAttributePairingResolutionDao::mapCacheRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Record attribute pairing cache hit returned no rows"));
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static AttributePairingCatalogRecord mapPairingRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new AttributePairingCatalogRecord(
                getLong(resultSet, "id"),
                resultSet.getString("pairing_cd"),
                resultSet.getString("pairing_nm"),
                resultSet.getString("schema_cd"),
                resultSet.getString("object_cd"),
                resultSet.getString("display_attribute_cd"),
                resultSet.getString("filter_attribute_cd"),
                resultSet.getString("pairing_type_cd"),
                resultSet.getString("lookup_strategy_cd"),
                resultSet.getString("lookup_inline_map_jsonb"),
                resultSet.getString("lookup_sql_template_txt"),
                resultSet.getBoolean("lookup_cache_enabled_flg"),
                getInteger(resultSet, "lookup_cache_ttl_seconds_nbr"),
                resultSet.getString("cardinality_cd"),
                resultSet.getBoolean("is_bidirectional_flg"),
                resultSet.getBoolean("is_cross_engine_flg"),
                resultSet.getBoolean("filter_attribute_indexed_flg"),
                resultSet.getString("filter_attribute_index_type_cd"),
                getInteger(resultSet, "performance_gain_pct_est_nbr"),
                resultSet.getString("ai_context_txt"),
                resultSet.getString("client_id"),
                resultSet.getString("lifecycle_status_cd"),
                resultSet.getString("governance_review_status_cd"),
                getInteger(resultSet, "version_nbr"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    private static AttributePairingValueCacheRecord mapCacheRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new AttributePairingValueCacheRecord(
                getLong(resultSet, "id"),
                resultSet.getString("pairing_cd"),
                resultSet.getString("client_id"),
                resultSet.getString("display_value_txt"),
                resultSet.getString("filter_value_txt"),
                resultSet.getBoolean("is_one_to_many_flg"),
                getLong(resultSet, "hit_count_nbr"),
                getOffsetDateTime(resultSet, "last_hit_ts"),
                getOffsetDateTime(resultSet, "cached_ts"),
                getOffsetDateTime(resultSet, "expires_ts")
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

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }
}
