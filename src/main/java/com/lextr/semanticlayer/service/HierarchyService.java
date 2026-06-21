package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.LogicalHierarchyDto;

import java.util.List;

public interface HierarchyService {

    List<LogicalHierarchyDto> findAll(String tenantCd);

    LogicalHierarchyDto createHierarchy(String hierarchyCd, String hierarchyNm, String tenantCd,
                                         String hierarchyStatusCd, String createdBy);
}
