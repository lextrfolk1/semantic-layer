package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.dto.DataConnectionDto;
import com.lextr.semanticlayer.dto.SchemaCatalogDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistryReadServiceImplTest {

    @Test
    void mapsSchemaRowsToDtosUsingEffectiveOverrideWhenPresent() {
        SchemaCatalogRecord record = new SchemaCatalogRecord(
                "meta",
                "Metadata",
                "Metadata Override",
                "Semantic system of record",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "flyway",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
        RegistryReadServiceImpl service = new RegistryReadServiceImpl(new FixedRegistryReadDao(List.of(record), List.of()));

        List<SchemaCatalogDto> results = service.findSchemas("client-a", "ACTIVE");

        assertEquals(1, results.size());
        assertEquals("meta", results.get(0).schema_cd());
        assertEquals("Metadata Override", results.get(0).schema_nm());
        assertEquals("Semantic system of record", results.get(0).schema_purpose_txt());
    }

    @Test
    void mapsConnectionRowsToDtosUsingEffectiveOverrideWhenPresent() {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        DataConnectionRecord record = new DataConnectionRecord(
                connectionId,
                "LEXTR_PG",
                "Lextr PostgreSQL",
                "Lextr PostgreSQL Override",
                "POSTGRES",
                "PRIMARY",
                "METADATA_PLUS_EXECUTION",
                "localhost",
                5432,
                "lextr",
                "meta",
                true,
                true,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "flyway",
                null,
                null
        );
        RegistryReadServiceImpl service = new RegistryReadServiceImpl(new FixedRegistryReadDao(List.of(), List.of(record)));

        List<DataConnectionDto> results = service.findConnections("client-a", "POSTGRES", true);

        assertEquals(1, results.size());
        assertEquals(connectionId, results.get(0).connection_id());
        assertEquals("Lextr PostgreSQL Override", results.get(0).connection_nm());
        assertEquals("POSTGRES", results.get(0).engine_cd());
    }

    @Test
    void fallsBackToBaseNamesWhenNoEffectiveOverrideExists() {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        DataConnectionRecord record = new DataConnectionRecord(
                connectionId,
                "LEXTR_PG",
                "Lextr PostgreSQL",
                "   ",
                "POSTGRES",
                "PRIMARY",
                "METADATA_PLUS_EXECUTION",
                "localhost",
                5432,
                "lextr",
                "meta",
                true,
                true,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "flyway",
                null,
                null
        );
        RegistryReadServiceImpl service = new RegistryReadServiceImpl(new FixedRegistryReadDao(List.of(), List.of(record)));

        DataConnectionDto result = service.findConnection("client-a", connectionId);

        assertEquals("Lextr PostgreSQL", result.connection_nm());
    }

    @Test
    void returns404WhenSchemaMissing() {
        RegistryReadServiceImpl service = new RegistryReadServiceImpl(new FixedRegistryReadDao(List.of(), List.of()));

        assertThrows(RegistryResourceNotFoundException.class, () -> service.findSchema("client-a", "missing"));
    }

    private static final class FixedRegistryReadDao implements RegistryReadDao {

        private final List<SchemaCatalogRecord> schemas;
        private final List<DataConnectionRecord> connections;

        private FixedRegistryReadDao(List<SchemaCatalogRecord> schemas, List<DataConnectionRecord> connections) {
            this.schemas = schemas;
            this.connections = connections;
        }

        @Override
        public List<SchemaCatalogRecord> findSchemas(String clientId, String lifecycleStatusCode) {
            return schemas;
        }

        @Override
        public Optional<SchemaCatalogRecord> findSchema(String clientId, String schemaCode) {
            return schemas.stream().filter(record -> schemaCode.equals(record.schema_cd())).findFirst();
        }

        @Override
        public List<DataConnectionRecord> findConnections(String clientId, String engineCode, Boolean activeFlag) {
            return connections;
        }

        @Override
        public Optional<DataConnectionRecord> findConnection(String clientId, UUID connectionId) {
            return connections.stream().filter(record -> connectionId.equals(record.connection_id())).findFirst();
        }
    }
}
