package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.LogicalHierarchyLevelRecord;
import com.lextr.semanticlayer.model.LogicalHierarchyRecord;
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

class JdbcHierarchyDaoTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private SQLQueryLoaderUtil sqlQueryLoaderUtil;
    private ObjectProvider<NamedParameterJdbcTemplate> provider;
    private JdbcHierarchyDao dao;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        sqlQueryLoaderUtil = mock(SQLQueryLoaderUtil.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        dao = new JdbcHierarchyDao(provider, sqlQueryLoaderUtil);
    }

    @Test
    void throwsIfJdbcTemplateNotConfigured() {
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcHierarchyDao nullDao = new JdbcHierarchyDao(provider, sqlQueryLoaderUtil);
        assertThrows(SemanticLayerException.class, () -> nullDao.findAll("GLOBAL"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllSuccess() {
        when(sqlQueryLoaderUtil.getQuery("logical_hierarchy.find_all")).thenReturn("SELECT * FROM logical_hierarchy");
        LogicalHierarchyRecord record = new LogicalHierarchyRecord(1L, "HIER-1", "Hierarchy", "GLOBAL", "ACTIVE", "system", null, null, null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        List<LogicalHierarchyRecord> result = dao.findAll("GLOBAL");
        assertEquals(1, result.size());
        assertEquals("HIER-1", result.get(0).hierarchy_cd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertSuccess() {
        when(sqlQueryLoaderUtil.getQuery("logical_hierarchy.insert")).thenReturn("INSERT INTO logical_hierarchy");
        LogicalHierarchyRecord record = new LogicalHierarchyRecord(1L, "HIER-1", "Hierarchy", "GLOBAL", "ACTIVE", "system", null, null, null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        LogicalHierarchyRecord result = dao.insert("HIER-1", "Hierarchy", "GLOBAL", "ACTIVE", "system");
        assertNotNull(result);
        assertEquals("HIER-1", result.hierarchy_cd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertThrowsIfEmpty() {
        when(sqlQueryLoaderUtil.getQuery("logical_hierarchy.insert")).thenReturn("INSERT INTO logical_hierarchy");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(SemanticLayerException.class, () -> dao.insert("HIER-1", "Hierarchy", "GLOBAL", "ACTIVE", "system"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findLevelsSuccess() {
        when(sqlQueryLoaderUtil.getQuery("logical_hierarchy.find_levels")).thenReturn("SELECT * FROM logical_hierarchy_level");
        LogicalHierarchyLevelRecord record = new LogicalHierarchyLevelRecord(1L, "HIER-1", 1, "Level 1", "org_node_nm", "org_node_cd", "ref.organization_hierarchy");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        List<LogicalHierarchyLevelRecord> result = dao.findLevels("HIER-1");
        assertEquals(1, result.size());
        assertEquals("org_node_nm", result.get(0).attribute_cd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertLevelSuccess() {
        when(sqlQueryLoaderUtil.getQuery("logical_hierarchy.insert_level")).thenReturn("INSERT INTO logical_hierarchy_level");
        LogicalHierarchyLevelRecord record = new LogicalHierarchyLevelRecord(1L, "HIER-1", 1, "Level 1", "org_node_nm", "org_node_cd", "ref.organization_hierarchy");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        LogicalHierarchyLevelRecord result = dao.insertLevel("HIER-1", 1, "Level 1", "org_node_nm", "org_node_cd", "ref.organization_hierarchy");
        assertNotNull(result);
        assertEquals(1, result.level_nbr());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertLevelThrowsIfEmpty() {
        when(sqlQueryLoaderUtil.getQuery("logical_hierarchy.insert_level")).thenReturn("INSERT INTO logical_hierarchy_level");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(SemanticLayerException.class, () -> dao.insertLevel("HIER-1", 1, "Level 1", "org_node_nm", "org_node_cd", "ref.organization_hierarchy"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void hierarchyRowMapperTest() throws Exception {
        when(sqlQueryLoaderUtil.getQuery("logical_hierarchy.find_all")).thenReturn("SELECT * FROM logical_hierarchy");
        ArgumentCaptor<RowMapper<LogicalHierarchyRecord>> captor = ArgumentCaptor.forClass(RowMapper.class);

        dao.findAll("GLOBAL");
        verify(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), captor.capture());

        RowMapper<LogicalHierarchyRecord> mapper = captor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(42L);
        when(rs.getString("hierarchy_cd")).thenReturn("HIER-42");
        when(rs.getString("hierarchy_nm")).thenReturn("Name");
        when(rs.getString("tenant_cd")).thenReturn("GLOBAL");
        when(rs.getString("hierarchy_status_cd")).thenReturn("ACTIVE");
        when(rs.getString("created_by")).thenReturn("system");
        when(rs.getTimestamp("created_ts")).thenReturn(Timestamp.from(Instant.parse("2026-06-23T10:00:00Z")));
        when(rs.getString("updated_by")).thenReturn("admin");
        when(rs.getTimestamp("updated_ts")).thenReturn(null);

        LogicalHierarchyRecord result = mapper.mapRow(rs, 1);
        assertNotNull(result);
        assertEquals(42L, result.id());
        assertEquals("HIER-42", result.hierarchy_cd());
        assertEquals("GLOBAL", result.tenant_cd());
        assertEquals("Name", result.hierarchy_nm());
        assertEquals("ACTIVE", result.hierarchy_status_cd());
        assertEquals("system", result.created_by());
        assertNotNull(result.created_ts());
        assertEquals("admin", result.updated_by());
        assertNull(result.updated_ts());
    }

    @Test
    @SuppressWarnings("unchecked")
    void levelRowMapperTest() throws Exception {
        when(sqlQueryLoaderUtil.getQuery("logical_hierarchy.find_levels")).thenReturn("SELECT * FROM logical_hierarchy_level");
        ArgumentCaptor<RowMapper<LogicalHierarchyLevelRecord>> captor = ArgumentCaptor.forClass(RowMapper.class);

        dao.findLevels("HIER-1");
        verify(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), captor.capture());

        RowMapper<LogicalHierarchyLevelRecord> mapper = captor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(100L);
        when(rs.getString("hierarchy_cd")).thenReturn("HIER-1");
        when(rs.getInt("level_nbr")).thenReturn(1);
        when(rs.getString("level_label")).thenReturn("Level 1");
        when(rs.getString("attribute_cd")).thenReturn("org_node_nm");
        when(rs.getString("code_cd")).thenReturn("org_node_cd");
        when(rs.getString("object_ref")).thenReturn("ref.organization_hierarchy");

        LogicalHierarchyLevelRecord result = mapper.mapRow(rs, 1);
        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals("HIER-1", result.hierarchy_cd());
        assertEquals(1, result.level_nbr());
        assertEquals("Level 1", result.level_label());
        assertEquals("org_node_nm", result.attribute_cd());
        assertEquals("org_node_cd", result.code_cd());
        assertEquals("ref.organization_hierarchy", result.object_ref());
    }
}
