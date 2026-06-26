package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.GovernanceHistoryReadDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.GovernanceHistoryEventRecord;
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

@Repository
public class JdbcGovernanceHistoryReadDao implements GovernanceHistoryReadDao {

    static final String FIND_BY_ENTITY = "governance_history.find_by_entity";

    private static final RowMapper<GovernanceHistoryEventRecord> ROW_MAPPER = (resultSet, rowNum) -> new GovernanceHistoryEventRecord(
            getLong(resultSet, "event_id"),
            resultSet.getString("client_id"),
            resultSet.getString("entity_type_cd"),
            resultSet.getString("entity_ref"),
            resultSet.getString("change_type_cd"),
            resultSet.getString("change_summary_txt"),
            resultSet.getString("actor_id"),
            resultSet.getObject("event_ts", OffsetDateTime.class),
            resultSet.getString("old_value_json"),
            resultSet.getString("new_value_json")
    );

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcGovernanceHistoryReadDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                        SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<GovernanceHistoryEventRecord> findEvents(String clientId,
                                                         String entityTypeCode,
                                                         String entityRef,
                                                         String changeTypeCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("entity_type_cd", entityTypeCode)
                .addValue("entity_ref", entityRef)
                .addValue("change_type_cd", changeTypeCode);
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_BY_ENTITY),
                parameters,
                ROW_MAPPER
        );
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static Long getLong(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).longValue();
    }
}
