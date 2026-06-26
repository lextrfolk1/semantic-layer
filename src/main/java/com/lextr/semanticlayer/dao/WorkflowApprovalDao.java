package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import java.time.OffsetDateTime;

public interface WorkflowApprovalDao {

    FilterLookupWorkflowTaskRecord findTaskById(String clientId, Long id);

    FilterLookupWorkflowTaskRecord findTaskByIdOnly(Long id);

    FilterLookupWorkflowTaskRecord approveTask(String clientId, Long id, String approvedBy, OffsetDateTime approvedTs, String approvalNote);

    FilterLookupWorkflowTaskRecord rejectTask(String clientId, Long id, String rejectedBy, OffsetDateTime rejectedTs, String rejectionNote);

    void approveLookup(String clientId, String lookupCd, String governanceStatus, OffsetDateTime updatedTs, String updatedBy);

    void approveAttributeOverride(String clientId, Long id, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy);

    void approveFilterLookupValue(String lookupCd, String valueCd, String lifecycleStatus, boolean validated, OffsetDateTime updatedTs);

    void approveObject(String clientId, String objectId, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy);

    void approvePairing(String clientId, String pairingCd, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy);

    void approveRelationship(String relationshipCd, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy);

    void rejectLookup(String clientId, String lookupCd, String governanceStatus, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy);

    void rejectAttributeOverride(String clientId, Long id, String overrideStatus, OffsetDateTime updatedTs, String updatedBy);

    void rejectObject(String clientId, String objectId, String lifecycleStatus, String governanceReviewStatus, OffsetDateTime updatedTs, String updatedBy);

    void rejectPairing(String clientId, String pairingCd, String lifecycleStatus, String governanceReviewStatus, OffsetDateTime updatedTs, String updatedBy);

    void rejectRelationship(String relationshipCd, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy);
}
