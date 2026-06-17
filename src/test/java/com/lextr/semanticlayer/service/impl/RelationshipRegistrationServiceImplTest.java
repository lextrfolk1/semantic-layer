package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.RelationshipRegistrationWriteDao;
import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RelationshipRegistrationServiceException;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipRegistrationServiceImplTest {

    @Test
    void mapsRequestToDaoAndReturnsResponse() {
        RecordingRelationshipRegistrationWriteDao dao = new RecordingRelationshipRegistrationWriteDao();
        dao.response = new SemanticRelationshipCatalogRecord(
                101L,
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                null,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer"
        );
        RelationshipRegistrationServiceImpl service = new RelationshipRegistrationServiceImpl(dao);

        RelationshipRegistrationResponseDto response = service.registerRelationship(new RelationshipRegistrationRequestDto(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "producer"
        ));

        assertEquals("GL_TO_LEDGER", dao.lastRequest.relationship_cd());
        assertEquals("ACTIVE", dao.lastRequest.lifecycle_status_cd());
        assertEquals("producer", dao.lastRequest.created_by());
        assertNotNull(dao.lastRequest.created_ts());
        assertEquals(101L, response.id());
        assertEquals("ACTIVE", response.lifecycle_status_cd());
        assertEquals("FOREIGN_KEY", response.relationship_type_cd());
    }

    @Test
    void rethrowsPolicyViolation() {
        RecordingRelationshipRegistrationWriteDao dao = new RecordingRelationshipRegistrationWriteDao();
        dao.error = new PolicyViolationException("POL-CE-001", "Cross-engine relationships are not allowed");
        RelationshipRegistrationServiceImpl service = new RelationshipRegistrationServiceImpl(dao);

        PolicyViolationException exception = assertThrows(PolicyViolationException.class, () -> service.registerRelationship(new RelationshipRegistrationRequestDto(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                true,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "producer"
        )));

        assertEquals("POL-CE-001", exception.code());
    }

    @Test
    void wrapsDaoFailuresInServiceException() {
        RecordingRelationshipRegistrationWriteDao dao = new RecordingRelationshipRegistrationWriteDao();
        dao.error = new IllegalStateException("dao failed");
        RelationshipRegistrationServiceImpl service = new RelationshipRegistrationServiceImpl(dao);

        RelationshipRegistrationServiceException exception = assertThrows(RelationshipRegistrationServiceException.class, () -> service.registerRelationship(new RelationshipRegistrationRequestDto(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "producer"
        )));

        assertTrue(exception.getMessage().contains("Unable to register relationship"));
    }

    private static final class RecordingRelationshipRegistrationWriteDao implements RelationshipRegistrationWriteDao {

        private SemanticRelationshipCatalogWriteRequest lastRequest;
        private SemanticRelationshipCatalogRecord response;
        private RuntimeException error;

        @Override
        public SemanticRelationshipCatalogRecord insertRelationship(SemanticRelationshipCatalogWriteRequest request) {
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
