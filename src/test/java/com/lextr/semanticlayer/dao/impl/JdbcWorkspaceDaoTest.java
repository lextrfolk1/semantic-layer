package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcWorkspaceDaoTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private SQLQueryLoaderUtil sqlQueryLoaderUtil;
    private ObjectProvider<NamedParameterJdbcTemplate> provider;
    private JdbcWorkspaceDao dao;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        sqlQueryLoaderUtil = mock(SQLQueryLoaderUtil.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        dao = new JdbcWorkspaceDao(provider, sqlQueryLoaderUtil);
    }

    @Test
    void throwsIfJdbcTemplateNotConfigured() {
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcWorkspaceDao nullDao = new JdbcWorkspaceDao(provider, sqlQueryLoaderUtil);
        assertThrows(SemanticLayerException.class, () -> nullDao.findAll("GLOBAL"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllSuccess() throws Exception {
        when(sqlQueryLoaderUtil.getQuery("tenant_workspace.find_all")).thenReturn("SELECT * FROM tenant_workspace");
        TenantWorkspaceRecord record = new TenantWorkspaceRecord(1L, "WS-CD", "GLOBAL", "Workspace", "Desc", "ACTIVE", "system", null, null, null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        List<TenantWorkspaceRecord> result = dao.findAll("GLOBAL");
        assertEquals(1, result.size());
        assertEquals("WS-CD", result.get(0).workspace_cd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertSuccess() throws Exception {
        when(sqlQueryLoaderUtil.getQuery("tenant_workspace.insert")).thenReturn("INSERT INTO tenant_workspace");
        TenantWorkspaceRecord record = new TenantWorkspaceRecord(1L, "WS-CD", "GLOBAL", "Workspace", "Desc", "ACTIVE", "system", null, null, null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        TenantWorkspaceRecord result = dao.insert("WS-CD", "GLOBAL", "Workspace", "Desc", "ACTIVE", "system");
        assertNotNull(result);
        assertEquals("WS-CD", result.workspace_cd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertThrowsIfEmpty() {
        when(sqlQueryLoaderUtil.getQuery("tenant_workspace.insert")).thenReturn("INSERT INTO tenant_workspace");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(SemanticLayerException.class, () -> dao.insert("WS-CD", "GLOBAL", "Workspace", "Desc", "ACTIVE", "system"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findObjectsByWorkspaceSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workspace_object.find_by_workspace")).thenReturn("SELECT * FROM workspace_object");
        WorkspaceObjectRecord record = new WorkspaceObjectRecord(1L, "WS-CD", "core", "gl_balance", "system", null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        List<WorkspaceObjectRecord> result = dao.findObjectsByWorkspace("WS-CD");
        assertEquals(1, result.size());
        assertEquals("gl_balance", result.get(0).object_cd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertObjectSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workspace_object.insert")).thenReturn("INSERT INTO workspace_object");
        WorkspaceObjectRecord record = new WorkspaceObjectRecord(1L, "WS-CD", "core", "gl_balance", "system", null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        WorkspaceObjectRecord result = dao.insertObject("WS-CD", "core", "gl_balance", "system");
        assertNotNull(result);
        assertEquals("gl_balance", result.object_cd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertObjectThrowsIfEmpty() {
        when(sqlQueryLoaderUtil.getQuery("workspace_object.insert")).thenReturn("INSERT INTO workspace_object");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(SemanticLayerException.class, () -> dao.insertObject("WS-CD", "core", "gl_balance", "system"));
    }

    @Test
    void deleteObjectSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workspace_object.delete")).thenReturn("DELETE FROM workspace_object");
        dao.deleteObject("WS-CD", "core", "gl_balance");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void workspaceRowMapperTest() throws Exception {
        when(sqlQueryLoaderUtil.getQuery("tenant_workspace.find_all")).thenReturn("SELECT * FROM tenant_workspace");
        ArgumentCaptor<RowMapper<TenantWorkspaceRecord>> captor = ArgumentCaptor.forClass(RowMapper.class);

        dao.findAll("GLOBAL");
        verify(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), captor.capture());

        RowMapper<TenantWorkspaceRecord> mapper = captor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(42L);
        when(rs.getString("workspace_cd")).thenReturn("WS-42");
        when(rs.getString("tenant_cd")).thenReturn("GLOBAL");
        when(rs.getString("workspace_nm")).thenReturn("Name");
        when(rs.getString("workspace_desc")).thenReturn("Desc");
        when(rs.getString("workspace_status_cd")).thenReturn("ACTIVE");
        when(rs.getString("created_by")).thenReturn("system");
        when(rs.getTimestamp("created_ts")).thenReturn(Timestamp.from(Instant.parse("2026-06-23T10:00:00Z")));
        when(rs.getString("updated_by")).thenReturn("admin");
        when(rs.getTimestamp("updated_ts")).thenReturn(null);

        TenantWorkspaceRecord result = mapper.mapRow(rs, 1);
        assertNotNull(result);
        assertEquals(42L, result.id());
        assertEquals("WS-42", result.workspace_cd());
        assertEquals("GLOBAL", result.tenant_cd());
        assertEquals("Name", result.workspace_nm());
        assertEquals("Desc", result.workspace_desc());
        assertEquals("ACTIVE", result.workspace_status_cd());
        assertEquals("system", result.created_by());
        assertNotNull(result.created_ts());
        assertEquals("admin", result.updated_by());
        assertNull(result.updated_ts());
    }

    @Test
    @SuppressWarnings("unchecked")
    void objectRowMapperTest() throws Exception {
        when(sqlQueryLoaderUtil.getQuery("workspace_object.find_by_workspace")).thenReturn("SELECT * FROM workspace_object");
        ArgumentCaptor<RowMapper<WorkspaceObjectRecord>> captor = ArgumentCaptor.forClass(RowMapper.class);

        dao.findObjectsByWorkspace("WS-CD");
        verify(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), captor.capture());

        RowMapper<WorkspaceObjectRecord> mapper = captor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(100L);
        when(rs.getString("workspace_cd")).thenReturn("WS-CD");
        when(rs.getString("schema_cd")).thenReturn("core");
        when(rs.getString("object_cd")).thenReturn("gl_balance");
        when(rs.getString("added_by")).thenReturn("user");
        when(rs.getTimestamp("added_ts")).thenReturn(Timestamp.from(Instant.parse("2026-06-23T10:00:00Z")));

        WorkspaceObjectRecord result = mapper.mapRow(rs, 1);
        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals("WS-CD", result.workspace_cd());
        assertEquals("core", result.schema_cd());
        assertEquals("gl_balance", result.object_cd());
        assertEquals("user", result.added_by());
        assertNotNull(result.added_ts());
    }
}
