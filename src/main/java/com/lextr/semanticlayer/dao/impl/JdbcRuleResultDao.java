package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.RuleResultDao;
import com.lextr.semanticlayer.exception.RuleResultServiceException;
import com.lextr.semanticlayer.model.ExternalRuleResultRecord;
import com.lextr.semanticlayer.model.ExternalRuleResultWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class JdbcRuleResultDao implements RuleResultDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcRuleResultDao.class);

    static final String INSERT_RESULT = "external_rule_result.insert_result";
    static final String INSERT_METADATA_CHANGE_HISTORY = "external_rule_result.insert_metadata_change_history";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcRuleResultDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                             SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public ExternalRuleResultRecord insertResult(ExternalRuleResultWriteRequest request) {
        logger.debug("Executing external rule result insert. clientId={}, outboundId={}, ruleRefCode={}, outputKindCode={}",
                request.client_id(), request.outbound_id(), request.rule_ref_cd(), request.output_kind_cd());
        ExternalRuleResultRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_RESULT),
                new MapSqlParameterSource()
                        .addValue("client_id", request.client_id())
                        .addValue("outbound_id", request.outbound_id())
                        .addValue("rule_ref_cd", request.rule_ref_cd())
                        .addValue("output_kind_cd", request.output_kind_cd())
                        .addValue("output_payload_jsonb", request.output_payload_jsonb())
                        .addValue("observed_ts", request.observed_ts())
                        .addValue("created_ts", request.created_ts())
                        .addValue("created_by", request.created_by())
                        .addValue("updated_ts", request.updated_ts())
                        .addValue("updated_by", request.updated_by()),
                JdbcRuleResultDao::mapResultRow
        ).stream().findFirst().orElseThrow(() -> new RuleResultServiceException("Insert external rule result returned no rows"));
        logger.debug("External rule result insert completed. clientId={}, outboundId={}, resultId={}",
                request.client_id(), request.outbound_id(), record.id());
        return record;
    }

    @Override
    public void insertMetadataChangeHistory(ObjectExposureAccessAuditWriteRequest request) {
        logger.debug("Executing external rule result metadata change insert. entityTypeCode={}, entityRef={}, changeTypeCode={}",
                request.entity_type_cd(), request.entity_ref(), request.change_type_cd());
        int affectedRows = jdbcTemplate().update(
                sqlQueryLoaderUtil.getQuery(INSERT_METADATA_CHANGE_HISTORY),
                new MapSqlParameterSource()
                        .addValue("entity_type_cd", request.entity_type_cd())
                        .addValue("entity_ref", request.entity_ref())
                        .addValue("change_type_cd", request.change_type_cd())
                        .addValue("changed_by", request.changed_by())
                        .addValue("changed_ts", request.changed_ts())
                        .addValue("change_reason_txt", request.change_reason_txt())
        );
        logger.debug("External rule result metadata change insert completed. entityTypeCode={}, entityRef={}, affectedRows={}",
                request.entity_type_cd(), request.entity_ref(), affectedRows);
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            logger.error("NamedParameterJdbcTemplate is not configured for rule result DAO.");
            throw new RuleResultServiceException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static ExternalRuleResultRecord mapResultRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExternalRuleResultRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("client_id"),
                resultSet.getObject("outbound_id", Long.class),
                resultSet.getString("rule_ref_cd"),
                resultSet.getString("output_kind_cd"),
                resultSet.getString("output_payload_jsonb"),
                resultSet.getObject("observed_ts", java.time.OffsetDateTime.class),
                resultSet.getObject("created_ts", java.time.OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getObject("updated_ts", java.time.OffsetDateTime.class),
                resultSet.getString("updated_by")
        );
    }
}
