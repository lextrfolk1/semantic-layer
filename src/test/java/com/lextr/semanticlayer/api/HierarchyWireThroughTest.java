package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dao.HierarchyDao;
import com.lextr.semanticlayer.model.LogicalHierarchyLevelRecord;
import com.lextr.semanticlayer.model.LogicalHierarchyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        HierarchyWireThroughTest.HierarchyWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class HierarchyWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RecordingHierarchyDao hierarchyDao;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        hierarchyDao.reset();
    }

    @Test
    void routesHierarchyListingThroughSpringAndMapsLevels() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-24T10:15:30Z");
        hierarchyDao.hierarchies.add(new LogicalHierarchyRecord(
                21L,
                "ENTITY_HIERARCHY",
                "Entity Hierarchy",
                "client-a",
                "ACTIVE",
                "platform",
                timestamp,
                "platform",
                timestamp
        ));
        hierarchyDao.levelsByHierarchy.put("ENTITY_HIERARCHY", List.of(
                new LogicalHierarchyLevelRecord(301L, "ENTITY_HIERARCHY", 1, "Entity", "entity_nm", "entity_cd", "ref.entity"),
                new LogicalHierarchyLevelRecord(302L, "ENTITY_HIERARCHY", 2, "Sub-Entity", "sub_entity_nm", "sub_entity_cd", "ref.sub_entity")
        ));

        mockMvc.perform(get("/api/hierarchies")
                        .queryParam("tenant_cd", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hierarchy_cd").value("ENTITY_HIERARCHY"))
                .andExpect(jsonPath("$[0].tenant_cd").value("client-a"))
                .andExpect(jsonPath("$[0].levels[0].level_nbr").value(1))
                .andExpect(jsonPath("$[0].levels[0].attribute_cd").value("entity_nm"))
                .andExpect(jsonPath("$[0].levels[1].object_ref").value("ref.sub_entity"));

        assertEquals("client-a", hierarchyDao.lastTenantCdQuery);
        assertEquals(List.of("ENTITY_HIERARCHY"), hierarchyDao.levelLookups);
    }

    @Test
    void routesHierarchyCreationThroughSpringAndDefaultsFields() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-24T10:15:30Z");
        hierarchyDao.insertedHierarchy = new LogicalHierarchyRecord(
                22L,
                "PERIOD_HIERARCHY",
                "Period Hierarchy",
                "client-b",
                "ACTIVE",
                "system",
                timestamp,
                "system",
                timestamp
        );
        hierarchyDao.levelsByHierarchy.put("PERIOD_HIERARCHY", List.of(
                new LogicalHierarchyLevelRecord(401L, "PERIOD_HIERARCHY", 1, "Year", "year_nm", "year_cd", "ref.year")
        ));

        mockMvc.perform(post("/api/hierarchies")
                        .contentType("application/json")
                        .content("""
                                {
                                  "hierarchy_cd": "PERIOD_HIERARCHY",
                                  "hierarchy_nm": "Period Hierarchy",
                                  "tenant_cd": "client-b"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hierarchy_cd").value("PERIOD_HIERARCHY"))
                .andExpect(jsonPath("$.tenant_cd").value("client-b"))
                .andExpect(jsonPath("$.hierarchy_status_cd").value("ACTIVE"))
                .andExpect(jsonPath("$.created_by").value("system"))
                .andExpect(jsonPath("$.levels[0].level_label").value("Year"));

        assertEquals("PERIOD_HIERARCHY", hierarchyDao.lastHierarchyCdInserted);
        assertEquals("Period Hierarchy", hierarchyDao.lastHierarchyNmInserted);
        assertEquals("client-b", hierarchyDao.lastTenantCdInserted);
        assertEquals("ACTIVE", hierarchyDao.lastHierarchyStatusCdInserted);
        assertEquals("system", hierarchyDao.lastCreatedByInserted);
        assertEquals(List.of("PERIOD_HIERARCHY"), hierarchyDao.levelLookups);
    }

    @TestConfiguration
    static class HierarchyWireThroughTestConfiguration {

        @Bean
        @Primary
        RecordingHierarchyDao recordingHierarchyDao() {
            return new RecordingHierarchyDao();
        }
    }

    static final class RecordingHierarchyDao implements HierarchyDao {

        private final List<LogicalHierarchyRecord> hierarchies = new ArrayList<>();
        private final Map<String, List<LogicalHierarchyLevelRecord>> levelsByHierarchy = new HashMap<>();
        private final List<String> levelLookups = new ArrayList<>();
        private String lastTenantCdQuery;
        private String lastHierarchyCdInserted;
        private String lastHierarchyNmInserted;
        private String lastTenantCdInserted;
        private String lastHierarchyStatusCdInserted;
        private String lastCreatedByInserted;
        private LogicalHierarchyRecord insertedHierarchy;

        void reset() {
            hierarchies.clear();
            levelsByHierarchy.clear();
            levelLookups.clear();
            lastTenantCdQuery = null;
            lastHierarchyCdInserted = null;
            lastHierarchyNmInserted = null;
            lastTenantCdInserted = null;
            lastHierarchyStatusCdInserted = null;
            lastCreatedByInserted = null;
            insertedHierarchy = null;
        }

        @Override
        public List<LogicalHierarchyRecord> findAll(String tenantCd) {
            lastTenantCdQuery = tenantCd;
            return hierarchies;
        }

        @Override
        public LogicalHierarchyRecord insert(String hierarchyCd, String hierarchyNm, String tenantCd,
                                             String hierarchyStatusCd, String createdBy) {
            lastHierarchyCdInserted = hierarchyCd;
            lastHierarchyNmInserted = hierarchyNm;
            lastTenantCdInserted = tenantCd;
            lastHierarchyStatusCdInserted = hierarchyStatusCd;
            lastCreatedByInserted = createdBy;
            return insertedHierarchy;
        }

        @Override
        public List<LogicalHierarchyLevelRecord> findLevels(String hierarchyCd) {
            levelLookups.add(hierarchyCd);
            return levelsByHierarchy.getOrDefault(hierarchyCd, List.of());
        }

        @Override
        public LogicalHierarchyLevelRecord insertLevel(String hierarchyCd, Integer levelNbr, String levelLabel,
                                                       String attributeCd, String codeCd, String objectRef) {
            throw new UnsupportedOperationException("Not used");
        }
    }
}
