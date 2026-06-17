package com.lextr.semanticlayer.util;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLQueryLoaderUtilTest {

    @Test
    void loadsRegistryQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String schemaQuery = loader.getQuery("schema_registry.find_all");
        String connectionQuery = loader.getQuery("connection_registry.find_all");

        assertTrue(schemaQuery.contains("schema_cd"));
        assertTrue(schemaQuery.contains(":lifecycle_status_cd"));
        assertTrue(connectionQuery.contains("connection_id"));
        assertTrue(connectionQuery.contains(":engine_cd"));
        assertTrue(connectionQuery.contains(":is_active_flg"));
        assertFalse(connectionQuery.contains("secrets_ref"));
    }

    @Test
    void failsWhenQueryKeyMissing() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        assertThrows(SemanticLayerException.class, () -> loader.getQuery("missing.query"));
    }
}
