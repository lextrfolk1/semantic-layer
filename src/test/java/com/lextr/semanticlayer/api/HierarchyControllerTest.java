package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.HierarchyDao;
import com.lextr.semanticlayer.model.LogicalHierarchyLevelRecord;
import com.lextr.semanticlayer.model.LogicalHierarchyRecord;
import com.lextr.semanticlayer.service.HierarchyService;
import com.lextr.semanticlayer.service.impl.HierarchyServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

class HierarchyControllerTest {

    @Test
    void listsHierarchiesSuccessfully() throws Exception {
        RecordingHierarchyDao dao = new RecordingHierarchyDao();
        dao.hierarchies.add(new LogicalHierarchyRecord(
                1L, "HIER-TEST", "Test Hierarchy", "test-tenant", "ACTIVE",
                "system", OffsetDateTime.parse("2026-06-23T10:00:00+05:30"), null, null
        ));
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/hierarchies")
                        .queryParam("tenant_cd", "test-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hierarchy_cd").value("HIER-TEST"))
                .andExpect(jsonPath("$[0].tenant_cd").value("test-tenant"));

        assertEquals("test-tenant", dao.lastTenantCdQuery);
    }

    @Test
    void createsHierarchySuccessfully() throws Exception {
        RecordingHierarchyDao dao = new RecordingHierarchyDao();
        MockMvc mockMvc = mockMvc(dao);

        Map<String, String> body = new HashMap<>();
        body.put("hierarchy_cd", "HIER-NEW");
        body.put("hierarchy_nm", "New Hierarchy");
        body.put("tenant_cd", "new-tenant");

        mockMvc.perform(post("/api/hierarchies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hierarchy_cd").value("HIER-NEW"))
                .andExpect(jsonPath("$.tenant_cd").value("new-tenant"));

        assertEquals("HIER-NEW", dao.lastHierarchyCdInserted);
        assertEquals("new-tenant", dao.lastTenantCdInserted);
    }

    private static MockMvc mockMvc(HierarchyDao dao) {
        HierarchyService service = new HierarchyServiceImpl(dao);
        HierarchyController controller = new HierarchyController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingHierarchyDao implements HierarchyDao {
        private final List<LogicalHierarchyRecord> hierarchies = new ArrayList<>();
        private String lastTenantCdQuery;
        private String lastHierarchyCdInserted;
        private String lastTenantCdInserted;

        @Override
        public List<LogicalHierarchyRecord> findAll(String tenantCd) {
            lastTenantCdQuery = tenantCd;
            return hierarchies;
        }

        @Override
        public LogicalHierarchyRecord insert(String hierarchyCd, String hierarchyNm, String tenantCd,
                                             String hierarchyStatusCd, String createdBy) {
            lastHierarchyCdInserted = hierarchyCd;
            lastTenantCdInserted = tenantCd;
            return new LogicalHierarchyRecord(
                    2L, hierarchyCd, hierarchyNm, tenantCd, hierarchyStatusCd,
                    createdBy, OffsetDateTime.now(), null, null
            );
        }

        @Override
        public List<LogicalHierarchyLevelRecord> findLevels(String hierarchyCd) {
            return List.of();
        }

        @Override
        public LogicalHierarchyLevelRecord insertLevel(String hierarchyCd, Integer levelNbr, String levelLabel,
                                                       String attributeCd, String codeCd, String objectRef) {
            return null;
        }
    }
}
