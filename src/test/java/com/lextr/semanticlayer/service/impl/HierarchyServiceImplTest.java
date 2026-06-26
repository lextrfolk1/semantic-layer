package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.HierarchyDao;
import com.lextr.semanticlayer.dto.LogicalHierarchyDto;
import com.lextr.semanticlayer.model.LogicalHierarchyLevelRecord;
import com.lextr.semanticlayer.model.LogicalHierarchyRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyServiceImplTest {

    @Test
    void mapsHierarchyRecordsAndLevelsIntoDto() {
        RecordingHierarchyDao dao = new RecordingHierarchyDao();
        OffsetDateTime createdTs = OffsetDateTime.parse("2026-06-24T10:15:30Z");
        OffsetDateTime updatedTs = OffsetDateTime.parse("2026-06-25T10:15:30Z");
        dao.hierarchies = List.of(new LogicalHierarchyRecord(
                31L,
                "ENTITY_HIERARCHY",
                "Entity Hierarchy",
                "client-a",
                "ACTIVE",
                "platform",
                createdTs,
                "reviewer",
                updatedTs
        ));
        dao.levels = List.of(
                new LogicalHierarchyLevelRecord(501L, "ENTITY_HIERARCHY", 1, "Entity", "entity_nm", "entity_cd", "ref.entity"),
                new LogicalHierarchyLevelRecord(502L, "ENTITY_HIERARCHY", 2, "Sub-Entity", "sub_entity_nm", "sub_entity_cd", "ref.sub_entity")
        );

        HierarchyServiceImpl service = new HierarchyServiceImpl(dao);

        List<LogicalHierarchyDto> result = service.findAll("client-a");

        assertEquals(1, result.size());
        LogicalHierarchyDto dto = result.get(0);
        assertEquals("ENTITY_HIERARCHY", dto.hierarchy_cd());
        assertEquals("client-a", dto.tenant_cd());
        assertEquals("ACTIVE", dto.hierarchy_status_cd());
        assertEquals("platform", dto.created_by());
        assertEquals(updatedTs, dto.updated_ts());
        assertEquals(2, dto.levels().size());
        assertEquals("Entity", dto.levels().get(0).level_label());
        assertEquals("ref.sub_entity", dto.levels().get(1).object_ref());
        assertEquals(List.of("client-a"), dao.tenantLookups);
        assertEquals(List.of("ENTITY_HIERARCHY"), dao.levelLookups);
    }

    @Test
    void createsHierarchyAndReturnsMappedDtoWithLevels() {
        RecordingHierarchyDao dao = new RecordingHierarchyDao();
        OffsetDateTime createdTs = OffsetDateTime.parse("2026-06-24T10:15:30Z");
        dao.insertedHierarchy = new LogicalHierarchyRecord(
                41L,
                "PERIOD_HIERARCHY",
                "Period Hierarchy",
                "client-b",
                "ACTIVE",
                "system",
                createdTs,
                "system",
                createdTs
        );
        dao.levels = List.of(new LogicalHierarchyLevelRecord(
                601L,
                "PERIOD_HIERARCHY",
                1,
                "Year",
                "year_nm",
                "year_cd",
                "ref.year"
        ));

        HierarchyServiceImpl service = new HierarchyServiceImpl(dao);

        LogicalHierarchyDto result = service.createHierarchy(
                "PERIOD_HIERARCHY",
                "Period Hierarchy",
                "client-b",
                "ACTIVE",
                "system"
        );

        assertEquals("PERIOD_HIERARCHY", result.hierarchy_cd());
        assertEquals("client-b", result.tenant_cd());
        assertEquals("ACTIVE", result.hierarchy_status_cd());
        assertEquals("system", result.created_by());
        assertEquals(1, result.levels().size());
        assertEquals("Year", result.levels().get(0).level_label());
        assertEquals(List.of("PERIOD_HIERARCHY"), dao.insertedHierarchyLookups);
        assertEquals(List.of("PERIOD_HIERARCHY"), dao.levelLookups);
    }

    private static final class RecordingHierarchyDao implements HierarchyDao {

        private List<LogicalHierarchyRecord> hierarchies = List.of();
        private List<LogicalHierarchyLevelRecord> levels = List.of();
        private final List<String> tenantLookups = new ArrayList<>();
        private final List<String> levelLookups = new ArrayList<>();
        private final List<String> insertedHierarchyLookups = new ArrayList<>();
        private LogicalHierarchyRecord insertedHierarchy;

        @Override
        public List<LogicalHierarchyRecord> findAll(String tenantCd) {
            tenantLookups.add(tenantCd);
            return hierarchies;
        }

        @Override
        public LogicalHierarchyRecord insert(String hierarchyCd, String hierarchyNm, String tenantCd,
                                             String hierarchyStatusCd, String createdBy) {
            insertedHierarchyLookups.add(hierarchyCd);
            return insertedHierarchy;
        }

        @Override
        public List<LogicalHierarchyLevelRecord> findLevels(String hierarchyCd) {
            levelLookups.add(hierarchyCd);
            return levels;
        }

        @Override
        public LogicalHierarchyLevelRecord insertLevel(String hierarchyCd, Integer levelNbr, String levelLabel,
                                                       String attributeCd, String codeCd, String objectRef) {
            throw new UnsupportedOperationException("Not used");
        }
    }
}
