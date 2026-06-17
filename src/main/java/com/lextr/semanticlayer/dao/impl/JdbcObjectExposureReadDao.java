package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
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
import java.util.UUID;

@Repository
public class JdbcObjectExposureReadDao implements ObjectExposureReadDao {

    static final String OBJECT_EXPOSURE_FIND_ALL = "object_exposure.find_all";
    static final String OBJECT_EXPOSURE_FIND_BY_ID = "object_exposure.find_by_id";
    static final String OBJECT_EXPOSURE_FIND_ATTRIBUTES_BY_OBJECT_ID = "object_exposure.find_attributes_by_object_id";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcObjectExposureReadDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                     SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("schema_cd", schemaCode)
                .addValue("lifecycle_status_cd", lifecycleStatusCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(OBJECT_EXPOSURE_FIND_ALL),
                parameters,
                objectExposureRowMapper()
        );
    }

    @Override
    public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("object_id", objectId);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(OBJECT_EXPOSURE_FIND_BY_ID),
                parameters,
                objectExposureRowMapper()
        ).stream().findFirst();
    }

    @Override
    public List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("object_id", objectId);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(OBJECT_EXPOSURE_FIND_ATTRIBUTES_BY_OBJECT_ID),
                parameters,
                attributeExposureRowMapper()
        );
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    static RowMapper<ObjectExposureRecord> objectExposureRowMapper() {
        return (resultSet, rowNum) -> new ObjectExposureRecord(
                getUuid(resultSet, "object_id"),
                resultSet.getString("client_id"),
                resultSet.getString("object_cd"),
                resultSet.getString("object_nm"),
                resultSet.getString("effective_object_nm"),
                resultSet.getString("object_type_cd"),
                resultSet.getString("schema_cd"),
                getUuid(resultSet, "connection_id"),
                resultSet.getString("lifecycle_status_cd"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    static RowMapper<AttributeExposureRecord> attributeExposureRowMapper() {
        return (resultSet, rowNum) -> new AttributeExposureRecord(
                getUuid(resultSet, "attribute_id"),
                getUuid(resultSet, "object_id"),
                resultSet.getString("client_id"),
                resultSet.getString("attribute_cd"),
                resultSet.getString("attribute_nm"),
                resultSet.getString("effective_attribute_nm"),
                resultSet.getString("data_type_cd"),
                resultSet.getString("taxonomy_cd"),
                resultSet.getString("taxonomy_source_cd"),
                resultSet.getString("taxonomy_jurisdiction_cd"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }

    private static UUID getUuid(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, UUID.class);
    }
}
