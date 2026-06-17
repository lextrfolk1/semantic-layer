package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
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
import java.util.UUID;

@Repository
public class JdbcRegistryReadDao implements RegistryReadDao {

    static final String SCHEMA_REGISTRY_FIND_ALL = "schema_registry.find_all";
    static final String CONNECTION_REGISTRY_FIND_ALL = "connection_registry.find_all";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcRegistryReadDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                               SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<SchemaCatalogRecord> findSchemas(String lifecycleStatusCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("lifecycle_status_cd", lifecycleStatusCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(SCHEMA_REGISTRY_FIND_ALL),
                parameters,
                schemaCatalogRowMapper()
        );
    }

    @Override
    public List<DataConnectionRecord> findConnections(String engineCode, Boolean activeFlag) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("engine_cd", engineCode)
                .addValue("is_active_flg", activeFlag);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(CONNECTION_REGISTRY_FIND_ALL),
                parameters,
                dataConnectionRowMapper()
        );
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    static RowMapper<SchemaCatalogRecord> schemaCatalogRowMapper() {
        return (resultSet, rowNum) -> new SchemaCatalogRecord(
                resultSet.getString("schema_cd"),
                resultSet.getString("schema_nm"),
                resultSet.getString("schema_purpose_txt"),
                resultSet.getString("lifecycle_status_cd"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    static RowMapper<DataConnectionRecord> dataConnectionRowMapper() {
        return (resultSet, rowNum) -> new DataConnectionRecord(
                getUuid(resultSet, "connection_id"),
                resultSet.getString("connection_cd"),
                resultSet.getString("connection_nm"),
                resultSet.getString("engine_cd"),
                resultSet.getString("connection_type_cd"),
                resultSet.getString("source_mode_cd"),
                resultSet.getString("host_nm"),
                getInteger(resultSet, "port_nbr"),
                resultSet.getString("database_nm"),
                resultSet.getString("schema_nm_default"),
                resultSet.getBoolean("is_default_flg"),
                resultSet.getBoolean("is_active_flg"),
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

    private static Integer getInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).intValue();
    }
}
