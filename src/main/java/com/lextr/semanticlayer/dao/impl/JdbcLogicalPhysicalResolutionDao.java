package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.LogicalPhysicalResolutionDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.LogicalPhysicalResolutionRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class JdbcLogicalPhysicalResolutionDao implements LogicalPhysicalResolutionDao {

    static final String FIND_BY_ATTRIBUTES = "logical_physical_resolution.find_by_attributes";
    static final String FIND_BY_OUTBOUND_GRAIN = "logical_physical_resolution.find_by_outbound_grain";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcLogicalPhysicalResolutionDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                            SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<LogicalPhysicalResolutionRecord> findByAttributes(String clientId,
                                                                  String schemaCode,
                                                                  String objectCode,
                                                                  List<String> logicalAttributeCodes) {
        if (logicalAttributeCodes == null || logicalAttributeCodes.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_BY_ATTRIBUTES),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("schema_cd", schemaCode)
                        .addValue("object_cd", objectCode)
                        .addValue("logical_attribute_cds", logicalAttributeCodes),
                JdbcLogicalPhysicalResolutionDao::mapRow
        );
    }

    @Override
    public List<LogicalPhysicalResolutionRecord> findByOutboundGrain(String clientId, Long outboundId) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_BY_OUTBOUND_GRAIN),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("outbound_id", outboundId),
                JdbcLogicalPhysicalResolutionDao::mapRow
        );
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static LogicalPhysicalResolutionRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new LogicalPhysicalResolutionRecord(
                getLong(resultSet, "outbound_id"),
                resultSet.getString("outbound_cd"),
                getInteger(resultSet, "grain_level_nbr"),
                resultSet.getString("client_id"),
                resultSet.getString("schema_cd"),
                resultSet.getString("object_cd"),
                resultSet.getString("logical_attribute_cd"),
                resultSet.getString("effective_logical_attribute_nm"),
                resultSet.getString("physical_attribute_nm"),
                resultSet.getString("source_object_nm"),
                resultSet.getString("engine_cd"),
                resultSet.getString("data_type_cd")
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
}
