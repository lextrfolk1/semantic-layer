package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRegistryReadDao implements RegistryReadDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcRegistryReadDao.class);

    static final String SCHEMA_REGISTRY_FIND_ALL = "schema_registry.find_all";
    static final String SCHEMA_REGISTRY_FIND_BY_CODE = "schema_registry.find_by_code";
    static final String CONNECTION_REGISTRY_FIND_ALL = "connection_registry.find_all";
    static final String CONNECTION_REGISTRY_FIND_BY_ID = "connection_registry.find_by_id";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcRegistryReadDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                               SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<SchemaCatalogRecord> findSchemas(String clientId, String lifecycleStatusCode) {
        logger.debug("Executing schema lookup. clientId={}, lifecycleStatusCode={}, queryKey={}", clientId, lifecycleStatusCode, SCHEMA_REGISTRY_FIND_ALL);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lifecycle_status_cd", lifecycleStatusCode);
        List<SchemaCatalogRecord> schemas = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(SCHEMA_REGISTRY_FIND_ALL),
                parameters,
                schemaCatalogRowMapper()
        );
        logger.debug("Schema lookup completed. clientId={}, resultCount={}", clientId, schemas.size());
        return schemas;
    }

    @Override
    public Optional<SchemaCatalogRecord> findSchema(String clientId, String schemaCode) {
        logger.debug("Executing schema lookup by code. clientId={}, schemaCode={}, queryKey={}", clientId, schemaCode, SCHEMA_REGISTRY_FIND_BY_CODE);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("schema_cd", schemaCode);
        List<SchemaCatalogRecord> schemas = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(SCHEMA_REGISTRY_FIND_BY_CODE),
                parameters,
                schemaCatalogRowMapper()
        );
        logger.debug("Schema lookup by code completed. clientId={}, schemaCode={}, resultCount={}", clientId, schemaCode, schemas.size());
        return schemas.stream().findFirst();
    }

    @Override
    public List<DataConnectionRecord> findConnections(String clientId, String engineCode, Boolean activeFlag) {
        logger.debug("Executing connection lookup. clientId={}, engineCode={}, activeFlag={}, queryKey={}", clientId, engineCode, activeFlag, CONNECTION_REGISTRY_FIND_ALL);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("engine_cd", engineCode)
                .addValue("is_active_flg", activeFlag);
        List<DataConnectionRecord> connections = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(CONNECTION_REGISTRY_FIND_ALL),
                parameters,
                dataConnectionRowMapper()
        );
        logger.debug("Connection lookup completed. clientId={}, resultCount={}", clientId, connections.size());
        return connections;
    }

    @Override
    public Optional<DataConnectionRecord> findConnection(String clientId, UUID connectionId) {
        logger.debug("Executing connection lookup by id. clientId={}, connectionId={}, queryKey={}", clientId, connectionId, CONNECTION_REGISTRY_FIND_BY_ID);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("connection_id", connectionId);
        List<DataConnectionRecord> connections = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(CONNECTION_REGISTRY_FIND_BY_ID),
                parameters,
                dataConnectionRowMapper()
        );
        logger.debug("Connection lookup by id completed. clientId={}, connectionId={}, resultCount={}", clientId, connectionId, connections.size());
        return connections.stream().findFirst();
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
                resultSet.getString("effective_schema_nm"),
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
                resultSet.getString("effective_connection_nm"),
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

    @Override
    public List<java.util.Map<String, Object>> introspectColumns(String schemaCd, String tableCd) {
        logger.debug("Executing column introspection query. schemaCode={}, tableCode={}", schemaCd, tableCd);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("schema_cd", schemaCd)
                .addValue("table_cd", tableCd);
        List<java.util.Map<String, Object>> columns = jdbcTemplate().queryForList(
                sqlQueryLoaderUtil.getQuery("schema_registry.introspect_columns"),
                parameters
        );
        logger.debug("Column introspection query completed. schemaCode={}, tableCode={}, resultCount={}", schemaCd, tableCd, columns.size());
        return columns;
    }

    @Override
    public List<java.util.Map<String, Object>> introspectTables(String schemaCd) {
        logger.debug("Executing table introspection query. schemaCode={}", schemaCd);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("schema_cd", schemaCd);
        List<java.util.Map<String, Object>> tables = jdbcTemplate().queryForList(
                sqlQueryLoaderUtil.getQuery("schema_registry.introspect_tables"),
                parameters
        );
        logger.debug("Table introspection query completed. schemaCode={}, resultCount={}", schemaCd, tables.size());
        return tables;
    }
}
