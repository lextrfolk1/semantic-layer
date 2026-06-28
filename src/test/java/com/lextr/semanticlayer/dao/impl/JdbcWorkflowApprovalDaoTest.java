package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcWorkflowApprovalDaoTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private SQLQueryLoaderUtil sqlQueryLoaderUtil;
    private ObjectProvider<NamedParameterJdbcTemplate> provider;
    private JdbcWorkflowApprovalDao dao;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        sqlQueryLoaderUtil = mock(SQLQueryLoaderUtil.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        dao = new JdbcWorkflowApprovalDao(provider, sqlQueryLoaderUtil);
    }

    @Test
    void throwsIfJdbcTemplateNotConfigured() {
        when(provider.getIfAvailable()).thenReturn(null);
        JdbcWorkflowApprovalDao nullDao = new JdbcWorkflowApprovalDao(provider, sqlQueryLoaderUtil);
        assertThrows(SemanticLayerException.class, () -> nullDao.findTaskById("GLOBAL", 1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findTaskByIdSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.find_task_by_id")).thenReturn("SELECT * FROM workflow_task");
        FilterLookupWorkflowTaskRecord record = new FilterLookupWorkflowTaskRecord(1L, "TYPE", "ENTITY", "REF", "PENDING", "user", null, "assign", null, "desc", "GLOBAL", null, null, null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        FilterLookupWorkflowTaskRecord result = dao.findTaskById("GLOBAL", 1L);
        assertNotNull(result);
        assertEquals("PENDING", result.task_status_cd());
    }

    @Test
    @SuppressWarnings("unchecked")
    void approveTaskSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_task")).thenReturn("UPDATE workflow_task");
        FilterLookupWorkflowTaskRecord record = new FilterLookupWorkflowTaskRecord(1L, "TYPE", "ENTITY", "REF", "APPROVED", "user", null, "assign", null, "desc", "GLOBAL", "admin", null, "note");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        FilterLookupWorkflowTaskRecord result = dao.approveTask("GLOBAL", 1L, "admin", OffsetDateTime.now(), "note");
        assertNotNull(result);
        assertEquals("APPROVED", result.task_status_cd());

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));
        assertEquals("GLOBAL", paramsCaptor.getValue().getValue("client_id"));
        assertEquals(1L, paramsCaptor.getValue().getValue("id"));
        assertEquals("admin", paramsCaptor.getValue().getValue("approved_by"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void approveTaskThrowsIfEmpty() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_task")).thenReturn("UPDATE workflow_task");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(SemanticLayerException.class, () -> dao.approveTask("GLOBAL", 1L, "admin", OffsetDateTime.now(), "note"));
    }

    @Test
    void approveLookupSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_lookup")).thenReturn("UPDATE lookup");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(1));
        dao.approveLookup("GLOBAL", "LK-1", "APPROVED", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void approveAttributeOverrideSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_attribute_override")).thenReturn("UPDATE override");
        dao.approveAttributeOverride("GLOBAL", 1L, "APPROVED", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void approveFilterLookupValueSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_filter_lookup_value")).thenReturn("UPDATE value");
        dao.approveFilterLookupValue("LK-1", "VAL-1", "APPROVED", true, OffsetDateTime.now());
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findTaskByIdOnlySuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.find_task_by_id_only")).thenReturn("SELECT * FROM task");
        FilterLookupWorkflowTaskRecord record = new FilterLookupWorkflowTaskRecord(1L, "TYPE", "ENTITY", "REF", "PENDING", "user", null, "assign", null, "desc", "GLOBAL", null, null, null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        FilterLookupWorkflowTaskRecord result = dao.findTaskByIdOnly(1L);
        assertNotNull(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectTaskSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_task")).thenReturn("UPDATE task");
        FilterLookupWorkflowTaskRecord record = new FilterLookupWorkflowTaskRecord(1L, "TYPE", "ENTITY", "REF", "REJECTED", "user", null, "assign", null, "desc", "GLOBAL", null, null, null);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(record));

        FilterLookupWorkflowTaskRecord result = dao.rejectTask("GLOBAL", 1L, "admin", OffsetDateTime.now(), "rejection note");
        assertNotNull(result);

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));
        assertEquals("GLOBAL", paramsCaptor.getValue().getValue("client_id"));
        assertEquals(1L, paramsCaptor.getValue().getValue("id"));
        assertEquals("admin", paramsCaptor.getValue().getValue("rejected_by"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectTaskThrowsIfEmpty() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_task")).thenReturn("UPDATE task");
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(SemanticLayerException.class, () -> dao.rejectTask("GLOBAL", 1L, "admin", OffsetDateTime.now(), "rejection note"));
    }

    @Test
    void approveObjectSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_object")).thenReturn("UPDATE object");
        dao.approveObject("GLOBAL", "OBJ-1", "APPROVED", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void approvePairingSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_pairing")).thenReturn("UPDATE pairing");
        dao.approvePairing("GLOBAL", "PAIR-1", "APPROVED", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void approveRelationshipSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_relationship")).thenReturn("UPDATE relationship");
        String relCd = "REL-1";
        UUID relUuid = UUID.nameUUIDFromBytes(relCd.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
                .thenReturn(Collections.singletonList(relCd));

        dao.approveRelationship(relUuid.toString(), "APPROVED", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void approveRelationshipThrowsIfUnresolved() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(SemanticLayerException.class, () ->
                dao.approveRelationship(UUID.randomUUID().toString(), "APPROVED", OffsetDateTime.now(), "admin"));
    }

    @Test
    void rejectLookupSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_lookup")).thenReturn("UPDATE lookup");
        dao.rejectLookup("GLOBAL", "LK-1", "REJECTED", "DRAFT", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void rejectAttributeOverrideSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_attribute_override")).thenReturn("UPDATE override");
        dao.rejectAttributeOverride("GLOBAL", 1L, "REJECTED", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void rejectObjectSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_object")).thenReturn("UPDATE object");
        dao.rejectObject("GLOBAL", "OBJ-1", "REJECTED", "REVIEW", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void rejectPairingSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_pairing")).thenReturn("UPDATE pairing");
        dao.rejectPairing("GLOBAL", "PAIR-1", "REJECTED", "REVIEW", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void rejectRelationshipSuccess() {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_relationship")).thenReturn("UPDATE relationship");
        String relCd = "REL-1";
        dao.rejectRelationship(relCd, "REJECTED", OffsetDateTime.now(), "admin");
        verify(jdbcTemplate).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void taskRowMapperTest() throws Exception {
        when(sqlQueryLoaderUtil.getQuery("workflow_approval.find_task_by_id")).thenReturn("SELECT * FROM task");
        ArgumentCaptor<RowMapper<FilterLookupWorkflowTaskRecord>> captor = ArgumentCaptor.forClass(RowMapper.class);

        dao.findTaskById("GLOBAL", 1L);
        verify(jdbcTemplate).query(anyString(), any(SqlParameterSource.class), captor.capture());

        RowMapper<FilterLookupWorkflowTaskRecord> mapper = captor.getValue();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1001L);
        when(rs.getString("task_type_cd")).thenReturn("TYPE");
        when(rs.getString("entity_type_cd")).thenReturn("ENTITY");
        when(rs.getString("entity_ref")).thenReturn("REF");
        when(rs.getString("task_status_cd")).thenReturn("PENDING");
        when(rs.getString("submitted_by")).thenReturn("user1");
        when(rs.getTimestamp("submitted_ts")).thenReturn(Timestamp.from(Instant.parse("2026-06-23T10:00:00Z")));
        when(rs.getString("assigned_to")).thenReturn("user2");
        when(rs.getDate("due_dt")).thenReturn(java.sql.Date.valueOf(LocalDate.of(2026, 6, 24)));
        when(rs.getString("description_txt")).thenReturn("desc");
        when(rs.getString("client_id")).thenReturn("GLOBAL");
        when(rs.getString("approved_by")).thenReturn("admin");
        when(rs.getTimestamp("approved_ts")).thenReturn(Timestamp.from(Instant.parse("2026-06-23T12:00:00Z")));
        when(rs.getString("approval_note_txt")).thenReturn("approved note");

        FilterLookupWorkflowTaskRecord result = mapper.mapRow(rs, 1);
        assertNotNull(result);
        assertEquals(1001L, result.id());
        assertEquals("TYPE", result.task_type_cd());
        assertEquals("ENTITY", result.entity_type_cd());
        assertEquals("REF", result.entity_ref());
        assertEquals("PENDING", result.task_status_cd());
        assertEquals("user1", result.submitted_by());
        assertNotNull(result.submitted_ts());
        assertEquals("user2", result.assigned_to());
        assertEquals(LocalDate.of(2026, 6, 24), result.due_dt());
        assertEquals("desc", result.description_txt());
        assertEquals("GLOBAL", result.client_id());
        assertEquals("admin", result.approved_by());
        assertNotNull(result.approved_ts());
        assertEquals("approved note", result.approval_note_txt());
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lextr/semanticlayer/dao/impl/JdbcWorkflowApprovalDao.java"));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
    }
}
