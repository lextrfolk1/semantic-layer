package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow-tasks")
@Tag(name = "Workflow", description = "Workflow approval operations.")
public class WorkflowTaskController {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskController.class);

    private final WorkflowApprovalService workflowApprovalService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    @Autowired
    public WorkflowTaskController(
            WorkflowApprovalService workflowApprovalService,
            ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
            SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.workflowApprovalService = workflowApprovalService;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @GetMapping
    @Operation(summary = "List workflow tasks", description = "Returns all workflow tasks, optionally filtered by client_id.")
    public List<WorkflowTaskResponseDto> listTasks(
            @Parameter(description = "Client ID filter.") @RequestParam(value = "client_id", required = false) String clientId) {
        if (jdbcTemplate == null) {
            logger.error("Workflow task listing failed because NamedParameterJdbcTemplate is not configured");
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        logger.debug("Listing workflow tasks. clientId={}", clientId);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId);
        List<WorkflowTaskResponseDto> tasks = jdbcTemplate.query(
                sqlQueryLoaderUtil.getQuery("workflow_task.find_all"),
                params,
                (rs, rowNum) -> new WorkflowTaskResponseDto(
                        rs.getLong("id"),
                        rs.getString("task_type_cd"),
                        rs.getString("entity_type_cd"),
                        rs.getString("entity_ref"),
                        rs.getString("task_status_cd"),
                        rs.getString("submitted_by"),
                        getOffsetDateTime(rs, "submitted_ts"),
                        rs.getString("assigned_to"),
                        getLocalDate(rs, "due_dt"),
                        rs.getString("description_txt"),
                        rs.getString("client_id"),
                        rs.getString("approved_by"),
                        getOffsetDateTime(rs, "approved_ts"),
                        rs.getString("approval_note_txt")
                )
        );
        logger.debug("Workflow tasks resolved. clientId={}, resultCount={}", clientId, tasks.size());
        return tasks;
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve workflow task", description = "Approves one workflow task and applies its side effects.")
    public WorkflowTaskResponseDto approveTask(
            @Parameter(description = "Workflow task identifier.") @PathVariable("id") Long id,
            @Valid @RequestBody WorkflowApprovalRequestDto request) {
        logger.debug("Approving workflow task. id={}, clientId={}", id, request.client_id());
        WorkflowTaskResponseDto task = workflowApprovalService.approveTask(id, request);
        logger.debug("Workflow task approved. id={}, clientId={}, taskStatusCode={}", id, request.client_id(), task.task_status_cd());
        return task;
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject workflow task", description = "Rejects a pending workflow task.")
    public WorkflowTaskResponseDto rejectTask(
            @Parameter(description = "Workflow task identifier.") @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        logger.debug(
                "Rejecting workflow task. id={}, clientId={}, rejectedByProvided={}",
                id,
                body.get("client_id"),
                body.containsKey("rejected_by") || body.containsKey("approved_by")
        );
        WorkflowTaskResponseDto task = workflowApprovalService.rejectTask(id, body);
        logger.debug("Workflow task rejected. id={}, clientId={}, taskStatusCode={}", id, task.client_id(), task.task_status_cd());
        return task;
    }

    private static OffsetDateTime getOffsetDateTime(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static LocalDate getLocalDate(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        java.sql.Date date = rs.getDate(col);
        return date == null ? null : date.toLocalDate();
    }
}
