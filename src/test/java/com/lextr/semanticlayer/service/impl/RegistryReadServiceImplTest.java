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
        assertEquals("ACTIVE", results.get(0).lifecycle_status_cd());
        assertEquals(OffsetDateTime.parse("2026-06-16T10:15:30+05:30"), results.get(0).created_ts());
        assertEquals("flyway", results.get(0).created_by());
        assertEquals(OffsetDateTime.parse("2026-06-17T10:15:30+05:30"), results.get(0).updated_ts());
        assertEquals("platform", results.get(0).updated_by());
    }

    @Test
    void fallsBackToBaseSchemaNameWhenNoEffectiveOverrideExists() {
        SchemaCatalogRecord record = new SchemaCatalogRecord(
                "meta",
                "Metadata",
                "   ",
                "Semantic system of record",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "flyway",
                null,
                null
        );
        RegistryReadServiceImpl service = new RegistryReadServiceImpl(new FixedRegistryReadDao(List.of(record), List.of()));

        SchemaCatalogDto result = service.findSchema("client-a", "meta");

        assertEquals("Metadata", result.schema_nm());
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
        assertEquals("LEXTR_PG", results.get(0).connection_cd());
        assertEquals("POSTGRES", results.get(0).engine_cd());
        assertEquals("PRIMARY", results.get(0).connection_type_cd());
        assertEquals("METADATA_PLUS_EXECUTION", results.get(0).source_mode_cd());
        assertEquals("localhost", results.get(0).host_nm());
        assertEquals(5432, results.get(0).port_nbr());
        assertEquals("lextr", results.get(0).database_nm());
        assertEquals("meta", results.get(0).schema_nm_default());
        assertEquals(true, results.get(0).is_default_flg());
        assertEquals(true, results.get(0).is_active_flg());
        assertEquals(OffsetDateTime.parse("2026-06-16T10:15:30+05:30"), results.get(0).created_ts());
        assertEquals("flyway", results.get(0).created_by());
        assertEquals(null, results.get(0).updated_ts());
        assertEquals(null, results.get(0).updated_by());
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

    @Test
    void returns404WhenConnectionMissing() {
        RegistryReadServiceImpl service = new RegistryReadServiceImpl(new FixedRegistryReadDao(List.of(), List.of()));

        assertThrows(
                RegistryResourceNotFoundException.class,
                () -> service.findConnection("client-a", UUID.fromString("00000000-0000-0000-0000-000000000099"))
        );
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
