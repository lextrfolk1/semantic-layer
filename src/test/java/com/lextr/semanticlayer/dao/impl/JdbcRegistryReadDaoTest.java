package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcRegistryReadDaoTest {

    @Test
    @SuppressWarnings("unchecked")
    void bindsSchemaParametersAndMapsSnakeCaseColumns() throws Exception {
        ObjectProvider<NamedParameterJdbcTemplate> provider = mock(ObjectProvider.class);
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);

        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());
        JdbcRegistryReadDao dao = new JdbcRegistryReadDao(provider, loader);

        OffsetDateTime created = OffsetDateTime.parse("2026-06-16T10:15:30+05:30");
        OffsetDateTime updated = OffsetDateTime.parse("2026-06-17T10:15:30+05:30");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            MapSqlParameterSource parameters = invocation.getArgument(1, MapSqlParameterSource.class);
            RowMapper<SchemaCatalogRecord> rowMapper = (RowMapper<SchemaCatalogRecord>) invocation.getArgument(2, RowMapper.class);

            assertTrue(sql.contains("SELECT schema_cd, schema_nm, schema_purpose_txt"));
            assertEquals("ACTIVE", parameters.getValue("lifecycle_status_cd"));

            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("schema_cd")).thenReturn("meta");
            when(resultSet.getString("schema_nm")).thenReturn("Metadata");
            when(resultSet.getString("schema_purpose_txt")).thenReturn("Semantic system of record");
            when(resultSet.getString("lifecycle_status_cd")).thenReturn("ACTIVE");
            when(resultSet.getObject("created_ts", OffsetDateTime.class)).thenReturn(created);
            when(resultSet.getString("created_by")).thenReturn("flyway");
            when(resultSet.getObject("updated_ts", OffsetDateTime.class)).thenReturn(updated);
            when(resultSet.getString("updated_by")).thenReturn("platform");
            return List.of(rowMapper.mapRow(resultSet, 0));
        });

        List<SchemaCatalogRecord> results = dao.findSchemas("ACTIVE");

        assertEquals(1, results.size());
        assertEquals("meta", results.get(0).schema_cd());
        assertEquals("Metadata", results.get(0).schema_nm());
        assertEquals("ACTIVE", results.get(0).lifecycle_status_cd());
        assertEquals(created, results.get(0).created_ts());
        assertEquals(updated, results.get(0).updated_ts());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bindsConnectionParametersAndWithholdsSecretsRef() throws Exception {
        ObjectProvider<NamedParameterJdbcTemplate> provider = mock(ObjectProvider.class);
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);

        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());
        JdbcRegistryReadDao dao = new JdbcRegistryReadDao(provider, loader);

        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        OffsetDateTime created = OffsetDateTime.parse("2026-06-16T10:15:30+05:30");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            MapSqlParameterSource parameters = invocation.getArgument(1, MapSqlParameterSource.class);
            RowMapper<DataConnectionRecord> rowMapper = (RowMapper<DataConnectionRecord>) invocation.getArgument(2, RowMapper.class);

            assertTrue(sql.contains("connection_id"));
            assertTrue(sql.contains("is_active_flg"));
            assertTrue(sql.contains("schema_nm_default"));
            assertTrue(!sql.contains("secrets_ref"));
            assertEquals("POSTGRES", parameters.getValue("engine_cd"));
            assertEquals(Boolean.TRUE, parameters.getValue("is_active_flg"));

            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getObject("connection_id", UUID.class)).thenReturn(connectionId);
            when(resultSet.getString("connection_cd")).thenReturn("LEXTR_PG");
            when(resultSet.getString("connection_nm")).thenReturn("Lextr PostgreSQL");
            when(resultSet.getString("engine_cd")).thenReturn("POSTGRES");
            when(resultSet.getString("connection_type_cd")).thenReturn("PRIMARY");
            when(resultSet.getString("source_mode_cd")).thenReturn("METADATA_PLUS_EXECUTION");
            when(resultSet.getString("host_nm")).thenReturn("localhost");
            when(resultSet.getObject("port_nbr")).thenReturn(5432);
            when(resultSet.getString("database_nm")).thenReturn("lextr");
            when(resultSet.getString("schema_nm_default")).thenReturn("meta");
            when(resultSet.getBoolean("is_default_flg")).thenReturn(true);
            when(resultSet.getBoolean("is_active_flg")).thenReturn(true);
            when(resultSet.getObject("created_ts", OffsetDateTime.class)).thenReturn(created);
            when(resultSet.getString("created_by")).thenReturn("flyway");
            when(resultSet.getObject("updated_ts", OffsetDateTime.class)).thenReturn(null);
            when(resultSet.getString("updated_by")).thenReturn(null);
            return List.of(rowMapper.mapRow(resultSet, 0));
        });

        List<DataConnectionRecord> results = dao.findConnections("POSTGRES", true);

        assertEquals(1, results.size());
        assertEquals(connectionId, results.get(0).connection_id());
        assertEquals("LEXTR_PG", results.get(0).connection_cd());
        assertEquals("POSTGRES", results.get(0).engine_cd());
        assertEquals("PRIMARY", results.get(0).connection_type_cd());
        assertEquals("meta", results.get(0).schema_nm_default());
        assertTrue(results.get(0).is_default_flg());
        assertTrue(results.get(0).is_active_flg());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        ObjectProvider<NamedParameterJdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        JdbcRegistryReadDao dao = new JdbcRegistryReadDao(provider, new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        assertThrows(SemanticLayerException.class, () -> dao.findSchemas(null));
    }
}
