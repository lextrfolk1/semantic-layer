package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.RelationshipRegistrationWriteDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;
import com.lextr.semanticlayer.model.SemanticRelationshipProjectionSyncWriteRequest;
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

@Repository
public class JdbcRelationshipRegistrationWriteDao implements RelationshipRegistrationWriteDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcRelationshipRegistrationWriteDao.class);

    static final String INSERT_RELATIONSHIP = "relationship_registration.insert_relationship";
    static final String UPDATE_NEO4J_PROJECTION_SYNC = "relationship_registration.update_neo4j_projection_sync";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcRelationshipRegistrationWriteDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                                SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public SemanticRelationshipCatalogRecord insertRelationship(SemanticRelationshipCatalogWriteRequest request) {
        logger.debug("Executing relationship insert. relationshipCode={}, parentSchemaCode={}, parentObjectCode={}, childSchemaCode={}, childObjectCode={}",
                request.relationship_cd(), request.parent_schema_cd(), request.parent_object_cd(),
                request.child_schema_cd(), request.child_object_cd());
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
        SemanticRelationshipCatalogRecord record =
                queryForRelationship(INSERT_RELATIONSHIP, parameters, "Insert relationship returned no rows");
        logger.debug("Relationship insert completed. relationshipCode={}, id={}", record.relationship_cd(), record.id());
        return record;
    }

    @Override
    public SemanticRelationshipCatalogRecord updateNeo4jProjectionSync(SemanticRelationshipProjectionSyncWriteRequest request) {
        logger.debug("Executing relationship projection sync update. relationshipCode={}, syncedTimestamp={}",
                request.relationship_cd(), request.neo4j_synced_ts());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("relationship_cd", request.relationship_cd())
                .addValue("neo4j_synced_ts", request.neo4j_synced_ts())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        SemanticRelationshipCatalogRecord record = queryForRelationship(
                UPDATE_NEO4J_PROJECTION_SYNC,
                parameters,
                "Update relationship projection sync returned no rows"
        );
        logger.debug("Relationship projection sync update completed. relationshipCode={}", record.relationship_cd());
        return record;
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            logger.error("NamedParameterJdbcTemplate is not configured for relationship registration DAO.");
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }

    private SemanticRelationshipCatalogRecord queryForRelationship(String queryKey,
                                                                   MapSqlParameterSource parameters,
                                                                   String emptyResultMessage) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(queryKey),
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
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException(emptyResultMessage));
    }

    private static Long getLong(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, Long.class);
    }
}
