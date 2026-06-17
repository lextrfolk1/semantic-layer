package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.RelationshipRegistrationWriteDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

@Repository
public class JdbcRelationshipRegistrationWriteDao implements RelationshipRegistrationWriteDao {

    static final String INSERT_RELATIONSHIP = "relationship_registration.insert_relationship";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcRelationshipRegistrationWriteDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                                SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public SemanticRelationshipCatalogRecord insertRelationship(SemanticRelationshipCatalogWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("relationship_cd", request.relationship_cd())
                .addValue("parent_schema_cd", request.parent_schema_cd())
                .addValue("parent_object_cd", request.parent_object_cd())
                .addValue("parent_attribute_cd", request.parent_attribute_cd())
                .addValue("child_schema_cd", request.child_schema_cd())
                .addValue("child_object_cd", request.child_object_cd())
                .addValue("child_attribute_cd", request.child_attribute_cd())
                .addValue("relationship_type_cd", request.relationship_type_cd())
                .addValue("cardinality_cd", request.cardinality_cd())
                .addValue("join_type_cd", request.join_type_cd())
                .addValue("is_enforced_flg", request.is_enforced_flg())
                .addValue("is_nullable_flg", request.is_nullable_flg())
                .addValue("is_cross_engine_flg", request.is_cross_engine_flg())
                .addValue("relationship_desc", request.relationship_desc())
                .addValue("ai_join_guidance_txt", request.ai_join_guidance_txt())
                .addValue("lifecycle_status_cd", request.lifecycle_status_cd())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_RELATIONSHIP),
                parameters,
                (resultSet, rowNum) -> new SemanticRelationshipCatalogRecord(
                        getLong(resultSet, "id"),
                        resultSet.getString("relationship_cd"),
                        resultSet.getString("parent_schema_cd"),
                        resultSet.getString("parent_object_cd"),
                        resultSet.getString("parent_attribute_cd"),
                        resultSet.getString("child_schema_cd"),
                        resultSet.getString("child_object_cd"),
                        resultSet.getString("child_attribute_cd"),
                        resultSet.getString("relationship_type_cd"),
                        resultSet.getString("cardinality_cd"),
                        resultSet.getString("join_type_cd"),
                        resultSet.getBoolean("is_enforced_flg"),
                        resultSet.getBoolean("is_nullable_flg"),
                        resultSet.getBoolean("is_cross_engine_flg"),
                        resultSet.getString("relationship_desc"),
                        resultSet.getString("ai_join_guidance_txt"),
                        getOffsetDateTime(resultSet, "neo4j_synced_ts"),
                        resultSet.getString("lifecycle_status_cd"),
                        getOffsetDateTime(resultSet, "created_ts"),
                        resultSet.getString("created_by"),
                        getOffsetDateTime(resultSet, "updated_ts"),
                        resultSet.getString("updated_by")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert relationship returned no rows"));
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }

    private static Long getLong(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, Long.class);
    }
}
