package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import java.time.OffsetDateTime;

public interface WorkflowApprovalDao {

    FilterLookupWorkflowTaskRecord findTaskById(String clientId, Long id);

    FilterLookupWorkflowTaskRecord approveTask(String clientId, Long id, String approvedBy, OffsetDateTime approvedTs, String approvalNote);

    void approveLookup(String clientId, String lookupCd, String governanceStatus, OffsetDateTime updatedTs, String updatedBy);

    void approveAttributeOverride(String clientId, Long id, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy);

    void approveFilterLookupValue(String lookupCd, String valueCd, String lifecycleStatus, boolean validated, OffsetDateTime updatedTs);
}
