package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.ProfilingResultReadDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.ProfilingResultRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class JdbcProfilingResultReadDao implements ProfilingResultReadDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcProfilingResultReadDao.class);

    static final String FIND_ALL = "profiling_result.find_all";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcProfilingResultReadDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                      SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<ProfilingResultRecord> findMetrics(String clientId, String schemaCode, String objectCode, String profilingStatusCode) {
        logger.debug("Executing profiling metric query. clientId={}, schemaCode={}, objectCode={}, profilingStatusCode={}",
                clientId, schemaCode, objectCode, profilingStatusCode);
        List<ProfilingResultRecord> records = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_ALL),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("schema_cd", schemaCode)
                        .addValue("object_cd", objectCode)
                        .addValue("profiling_status_cd", profilingStatusCode),
                JdbcProfilingResultReadDao::mapRow
        );
        logger.debug("Profiling metric query completed. clientId={}, schemaCode={}, objectCode={}, resultCount={}",
                clientId, schemaCode, objectCode, records.size());
        return records;
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            logger.error("NamedParameterJdbcTemplate is not configured for profiling result DAO.");
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static ProfilingResultRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ProfilingResultRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("client_id"),
                resultSet.getString("schema_cd"),
                resultSet.getString("object_cd"),
                resultSet.getString("logical_attribute_cd"),
                resultSet.getString("attribute_role_cd"),
                getInteger(resultSet, "null_pct_nbr"),
                getInteger(resultSet, "distinct_pct_nbr"),
                resultSet.getString("profiling_status_cd"),
                resultSet.getObject("last_profiled_ts", OffsetDateTime.class),
                resultSet.getObject("created_ts", OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getObject("updated_ts", OffsetDateTime.class),
                resultSet.getString("updated_by")
        );
    }

    private static Integer getInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).intValue();
    }
}
