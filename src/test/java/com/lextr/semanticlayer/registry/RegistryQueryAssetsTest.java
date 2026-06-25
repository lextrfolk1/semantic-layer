package com.lextr.semanticlayer.registry;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryQueryAssetsTest {

    @Test
    void loadsTenantAwareRegistryQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String schemaListQuery = loader.getQuery("schema_registry.find_all");
        String schemaByCodeQuery = loader.getQuery("schema_registry.find_by_code");
        String connectionListQuery = loader.getQuery("connection_registry.find_all");
        String connectionByIdQuery = loader.getQuery("connection_registry.find_by_id");

        assertTrue(schemaListQuery.contains("FROM meta.schema_catalog"));
        assertTrue(schemaListQuery.contains("WHERE client_id = :client_id"));
        assertTrue(schemaListQuery.contains("schema_cd"));
        assertTrue(schemaListQuery.contains("schema_nm"));
        assertTrue(schemaListQuery.contains("effective_schema_nm"));
        assertTrue(schemaListQuery.contains("lifecycle_status_cd"));
        assertTrue(schemaListQuery.contains("created_ts"));
        assertTrue(schemaListQuery.contains("updated_by"));

        assertTrue(schemaByCodeQuery.contains("FROM meta.schema_catalog"));
        assertTrue(schemaByCodeQuery.contains("WHERE client_id = :client_id"));
        assertTrue(schemaByCodeQuery.contains("schema_cd = :schema_cd"));
        assertTrue(schemaByCodeQuery.contains("effective_schema_nm"));

        assertTrue(connectionListQuery.contains("FROM meta.data_connection"));
        assertTrue(connectionListQuery.contains("WHERE client_id = :client_id"));
        assertTrue(connectionListQuery.contains("connection_id"));
        assertTrue(connectionListQuery.contains("connection_cd"));
        assertTrue(connectionListQuery.contains("effective_connection_nm"));
        assertTrue(connectionListQuery.contains("engine_cd"));
        assertTrue(connectionListQuery.contains("connection_type_cd"));
        assertTrue(connectionListQuery.contains("schema_nm_default"));
        assertTrue(connectionListQuery.contains("is_active_flg"));
        assertFalse(connectionListQuery.contains("secrets_ref"));

        assertTrue(connectionByIdQuery.contains("FROM meta.data_connection"));
        assertTrue(connectionByIdQuery.contains("WHERE client_id = :client_id"));
        assertTrue(connectionByIdQuery.contains("connection_id = :connection_id"));
        assertTrue(connectionByIdQuery.contains("effective_connection_nm"));
        assertFalse(connectionByIdQuery.contains("secrets_ref"));
    }
}
