package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.exception.SemanticLayerException;
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

@Repository
public class JdbcGovernancePolicyPresetReadDao implements GovernancePolicyPresetReadDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcGovernancePolicyPresetReadDao.class);

    static final String FIND_BY_CODE = "governance_policy_preset.find_by_code";
    static final String FIND_ALL = "governance_policy_preset.find_all";

    private static final RowMapper<GovernancePolicyPresetRecord> ROW_MAPPER = (resultSet, rowNum) -> new GovernancePolicyPresetRecord(
            resultSet.getString("policy_cd"),
            resultSet.getString("policy_nm"),
            resultSet.getString("policy_scope_cd"),
            resultSet.getString("default_value_txt"),
            resultSet.getString("data_type_cd"),
            resultSet.getBoolean("is_overrideable_flg"),
            resultSet.getBoolean("override_requires_approval_flg"),
            getLocalDate(resultSet, "effective_from_dt"),
            getLocalDate(resultSet, "effective_to_dt"),
            resultSet.getString("approved_by"),
            getOffsetDateTime(resultSet, "approved_ts"),
            getOffsetDateTime(resultSet, "created_ts"),
            resultSet.getString("created_by")
    );

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcGovernancePolicyPresetReadDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                             SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public Optional<GovernancePolicyPresetRecord> findPolicyPreset(String policyCode,
                                                                   String policyScopeCode,
                                                                   LocalDate asOfDate) {
        logger.debug("Executing governance policy preset query by code. policyCode={}, policyScopeCode={}, asOfDate={}",
                policyCode, policyScopeCode, asOfDate);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("policy_cd", policyCode)
                .addValue("policy_scope_cd", policyScopeCode)
                .addValue("as_of_dt", asOfDate);
        Optional<GovernancePolicyPresetRecord> record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_BY_CODE),
                parameters,
                ROW_MAPPER
        ).stream().findFirst();
        logger.debug("Governance policy preset query by code completed. policyCode={}, policyScopeCode={}, found={}",
                policyCode, policyScopeCode, record.isPresent());
        return record;
    }

    @Override
    public List<GovernancePolicyPresetRecord> findPolicyPresets(String policyScopeCode, LocalDate asOfDate) {
        logger.debug("Executing governance policy preset list query. policyScopeCode={}, asOfDate={}", policyScopeCode, asOfDate);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("policy_scope_cd", policyScopeCode)
                .addValue("as_of_dt", asOfDate);
        List<GovernancePolicyPresetRecord> records = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_ALL),
                parameters,
                ROW_MAPPER
        );
        logger.debug("Governance policy preset list query completed. policyScopeCode={}, resultCount={}",
                policyScopeCode, records.size());
        return records;
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            logger.error("NamedParameterJdbcTemplate is not configured for governance policy preset DAO.");
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static LocalDate getLocalDate(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, LocalDate.class);
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }
}
