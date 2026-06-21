package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.HierarchyDao;
import com.lextr.semanticlayer.dto.LogicalHierarchyDto;
import com.lextr.semanticlayer.model.LogicalHierarchyLevelRecord;
import com.lextr.semanticlayer.model.LogicalHierarchyRecord;
import com.lextr.semanticlayer.service.HierarchyService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HierarchyServiceImpl implements HierarchyService {

    private final HierarchyDao hierarchyDao;

    public HierarchyServiceImpl(HierarchyDao hierarchyDao) {
        this.hierarchyDao = hierarchyDao;
    }

    @Override
    public List<LogicalHierarchyDto> findAll(String tenantCd) {
        List<LogicalHierarchyRecord> hierarchies = hierarchyDao.findAll(tenantCd);
        return hierarchies.stream().map(this::toDto).toList();
    }

    @Override
    public LogicalHierarchyDto createHierarchy(String hierarchyCd, String hierarchyNm, String tenantCd,
                                                String hierarchyStatusCd, String createdBy) {
        LogicalHierarchyRecord record = hierarchyDao.insert(hierarchyCd, hierarchyNm, tenantCd,
                hierarchyStatusCd, createdBy);
        return toDto(record);
    }

    private LogicalHierarchyDto toDto(LogicalHierarchyRecord record) {
        List<LogicalHierarchyLevelRecord> levels = hierarchyDao.findLevels(record.hierarchy_cd());
        List<LogicalHierarchyDto.HierarchyLevelDto> levelDtos = levels.stream()
                .map(l -> new LogicalHierarchyDto.HierarchyLevelDto(
                        l.level_nbr(), l.level_label(), l.attribute_cd(), l.code_cd(), l.object_ref()))
                .toList();

        return new LogicalHierarchyDto(
                record.id(),
                record.hierarchy_cd(),
                record.hierarchy_nm(),
                record.tenant_cd(),
                record.hierarchy_status_cd(),
                record.created_by(),
                record.created_ts(),
                record.updated_by(),
                record.updated_ts(),
                levelDtos
        );
    }
}
