package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.RelationshipRegistrationWriteDao;
import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RelationshipRegistrationServiceException;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;
import com.lextr.semanticlayer.service.RelationshipRegistrationService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class RelationshipRegistrationServiceImpl implements RelationshipRegistrationService {

    private static final String ACTIVE_LIFECYCLE_STATUS_CD = "ACTIVE";

    private final RelationshipRegistrationWriteDao relationshipRegistrationWriteDao;

    public RelationshipRegistrationServiceImpl(RelationshipRegistrationWriteDao relationshipRegistrationWriteDao) {
        this.relationshipRegistrationWriteDao = relationshipRegistrationWriteDao;
    }

    @Override
    public RelationshipRegistrationResponseDto registerRelationship(RelationshipRegistrationRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            SemanticRelationshipCatalogRecord record = relationshipRegistrationWriteDao.insertRelationship(
                    new SemanticRelationshipCatalogWriteRequest(
                            request.relationship_cd(),
                            request.parent_schema_cd(),
                            request.parent_object_cd(),
                            request.parent_attribute_cd(),
                            request.child_schema_cd(),
                            request.child_object_cd(),
                            request.child_attribute_cd(),
                            request.relationship_type_cd(),
                            request.cardinality_cd(),
                            request.join_type_cd(),
                            request.is_enforced_flg(),
                            request.is_nullable_flg(),
                            request.is_cross_engine_flg(),
                            request.relationship_desc(),
                            request.ai_join_guidance_txt(),
                            ACTIVE_LIFECYCLE_STATUS_CD,
                            now,
                            request.registered_by(),
                            now,
                            request.registered_by()
                    )
            );
            return toResponse(record);
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RelationshipRegistrationServiceException("Unable to register relationship", exception);
        }
    }

    private RelationshipRegistrationResponseDto toResponse(SemanticRelationshipCatalogRecord record) {
        return new RelationshipRegistrationResponseDto(
                record.id(),
                record.relationship_cd(),
                record.parent_schema_cd(),
                record.parent_object_cd(),
                record.parent_attribute_cd(),
                record.child_schema_cd(),
                record.child_object_cd(),
                record.child_attribute_cd(),
                record.relationship_type_cd(),
                record.cardinality_cd(),
                record.join_type_cd(),
                record.is_enforced_flg(),
                record.is_nullable_flg(),
                record.is_cross_engine_flg(),
                record.relationship_desc(),
                record.ai_join_guidance_txt(),
                record.neo4j_synced_ts(),
                record.lifecycle_status_cd()
        );
    }
}
