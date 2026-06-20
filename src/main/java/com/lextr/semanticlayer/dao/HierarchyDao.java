package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.LogicalHierarchyLevelRecord;
import com.lextr.semanticlayer.model.LogicalHierarchyRecord;

import java.util.List;

public interface HierarchyDao {

    List<LogicalHierarchyRecord> findAll(String tenantCd);

    LogicalHierarchyRecord insert(String hierarchyCd, String hierarchyNm, String tenantCd,
                                   String hierarchyStatusCd, String createdBy);

    List<LogicalHierarchyLevelRecord> findLevels(String hierarchyCd);

    LogicalHierarchyLevelRecord insertLevel(String hierarchyCd, Integer levelNbr, String levelLabel,
                                             String attributeCd, String codeCd, String objectRef);
}
