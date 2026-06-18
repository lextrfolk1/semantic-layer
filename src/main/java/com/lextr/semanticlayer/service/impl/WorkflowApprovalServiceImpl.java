package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dao.WorkflowApprovalDao;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyRequestDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyDecisionDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.WorkflowApprovalServiceException;
import com.lextr.semanticlayer.exception.WorkflowTaskAlreadyApprovedException;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import com.lextr.semanticlayer.service.WorkflowPolicyClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class WorkflowApprovalServiceImpl implements WorkflowApprovalService {

    private final WorkflowApprovalDao workflowApprovalDao;
    private final FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao;
    private final WorkflowPolicyClient workflowPolicyClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public WorkflowApprovalServiceImpl(
            ObjectProvider<WorkflowApprovalDao> workflowApprovalDaoProvider,
            ObjectProvider<FilterLookupRegistrationWriteDao> filterLookupRegistrationWriteDaoProvider,
            ObjectProvider<WorkflowPolicyClient> workflowPolicyClientProvider,
            @Qualifier("semanticLayerTransactionOperations")
            ObjectProvider<TransactionOperations> transactionOperationsProvider
    ) {
        this(
                workflowApprovalDaoProvider.getIfAvailable(),
                filterLookupRegistrationWriteDaoProvider.getIfAvailable(),
                workflowPolicyClientProvider.getIfAvailable(() -> request -> new WorkflowPolicyDecisionDto(true, null, null)),
                transactionOperationsProvider.getIfAvailable()
        );
    }

    public WorkflowApprovalServiceImpl(
            WorkflowApprovalDao workflowApprovalDao,
            FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao,
            WorkflowPolicyClient workflowPolicyClient,
            TransactionOperations transactionOperations
    ) {
        this.workflowApprovalDao = workflowApprovalDao;
        this.filterLookupRegistrationWriteDao = filterLookupRegistrationWriteDao;
        this.workflowPolicyClient = workflowPolicyClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
        FilterLookupWorkflowTaskRecord task = workflowApprovalDao.findTaskById(request.client_id(), id);
        if (task == null) {
            throw new RegistryResourceNotFoundException("workflow task", String.valueOf(id));
        }

        if ("APPROVED".equalsIgnoreCase(task.task_status_cd())) {
            throw new WorkflowTaskAlreadyApprovedException(id);
        }

        // OPA authorization policy validation
        WorkflowPolicyDecisionDto decision = workflowPolicyClient.validateApproval(
                new WorkflowPolicyRequestDto(
                        request.client_id(),
                        task.id(),
                        task.task_type_cd(),
                        task.entity_type_cd(),
                        task.entity_ref(),
                        task.submitted_by(),
                        request.approved_by()
                )
        );

        if (!decision.allowed()) {
            throw new PolicyViolationException(decision.code(), decision.message());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            return transactionOperations.execute(status -> {
                // Update workflow task status
                FilterLookupWorkflowTaskRecord approvedTask = workflowApprovalDao.approveTask(
                        request.client_id(),
                        id,
                        request.approved_by(),
                        now,
                        request.approval_note_txt()
                );

                // Side effect application based on task type
                executeSideEffect(task, request.approved_by(), now);

                // Write audit log entry
                filterLookupRegistrationWriteDao.insertMetadataChangeHistory(new FilterLookupMetadataChangeHistoryWriteRequest(
                        task.entity_type_cd(),
                        task.entity_ref(),
                        "APPROVED",
                        request.approved_by(),
                        now,
                        null,
                        null,
                        "Approved workflow task " + id + " (" + task.task_type_cd() + ") for " + task.entity_ref()
                ));

                return toResponseDto(approvedTask);
            });
        } catch (PolicyViolationException | WorkflowTaskAlreadyApprovedException | RegistryResourceNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WorkflowApprovalServiceException("Unable to approve workflow task " + id, exception);
        }
    }

    private void executeSideEffect(FilterLookupWorkflowTaskRecord task, String approvedBy, OffsetDateTime now) {
        String type = task.task_type_cd();
        if ("FILTER_LOOKUP_REGISTRATION".equalsIgnoreCase(type)) {
            workflowApprovalDao.approveLookup(task.client_id(), task.entity_ref(), "ACTIVE", now, approvedBy);
        } else if ("ATTRIBUTE_LOGICAL_NAME_OVERRIDE".equalsIgnoreCase(type)) {
            try {
                Long overrideId = Long.parseLong(task.entity_ref());
                workflowApprovalDao.approveAttributeOverride(task.client_id(), overrideId, "ACTIVE", now, approvedBy);
            } catch (NumberFormatException ignored) {
            }
        } else if ("FILTER_LOOKUP_VALUE".equalsIgnoreCase(type)) {
            String[] parts = task.entity_ref().split(":", 2);
            if (parts.length == 2) {
                workflowApprovalDao.approveFilterLookupValue(parts[0], parts[1], "ACTIVE", true, now);
            }
        }
    }

    private WorkflowTaskResponseDto toResponseDto(FilterLookupWorkflowTaskRecord record) {
        return new WorkflowTaskResponseDto(
                record.id(),
                record.task_type_cd(),
                record.entity_type_cd(),
                record.entity_ref(),
                record.task_status_cd(),
                record.submitted_by(),
                record.submitted_ts(),
                record.assigned_to(),
                record.due_dt(),
                record.description_txt(),
                record.client_id(),
                record.approved_by(),
                record.approved_ts(),
                record.approval_note_txt()
        );
    }
}
