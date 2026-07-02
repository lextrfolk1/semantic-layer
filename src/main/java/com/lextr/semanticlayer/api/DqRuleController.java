package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.DqRuleAttributeDto;
import com.lextr.semanticlayer.dto.DqRuleCatalogDto;
import com.lextr.semanticlayer.dto.DqRuleRequestDto;
import com.lextr.semanticlayer.dto.DqRuleResultDto;
import com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.DqRuleServiceException;
import com.lextr.semanticlayer.service.DqRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dq-rules")
@Tag(name = "DQ Rules", description = "Data quality rule catalog, request, and observe operations.")
public class DqRuleController {

    private static final Logger logger = LoggerFactory.getLogger(DqRuleController.class);

    private final DqRuleService dqRuleService;

    @Autowired
    public DqRuleController(ObjectProvider<DqRuleService> dqRuleServiceProvider) {
        this(dqRuleServiceProvider.getIfAvailable(MissingDqRuleService::new));
    }

    DqRuleController(DqRuleService dqRuleService) {
        this.dqRuleService = dqRuleService;
    }

    @GetMapping
    @Operation(summary = "List DQ rules", description = "Returns rule catalog entries visible for the supplied client.")
    public List<DqRuleCatalogDto> findRules(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional rule dimension filter.") @RequestParam(value = "rule_dimension_cd", required = false) String ruleDimensionCode,
            @Parameter(description = "Optional lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        logger.debug("Listing DQ rules. clientId={}, ruleDimensionCode={}, lifecycleStatusCode={}", clientId, ruleDimensionCode, lifecycleStatusCode);
        List<DqRuleCatalogDto> rules = dqRuleService.findRules(clientId, ruleDimensionCode, lifecycleStatusCode);
        logger.debug("DQ rules resolved. clientId={}, resultCount={}", clientId, rules.size());
        return rules;
    }

    @GetMapping("/{rule_cd}")
    @Operation(summary = "Get DQ rule", description = "Returns one DQ rule for the supplied client.")
    public DqRuleCatalogDto findRule(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Rule code.") @PathVariable("rule_cd") String ruleCode) {
        logger.debug("Fetching DQ rule. clientId={}, ruleCode={}", clientId, ruleCode);
        DqRuleCatalogDto rule = dqRuleService.findRule(clientId, ruleCode);
        logger.debug("DQ rule resolved. clientId={}, ruleCode={}", clientId, ruleCode);
        return rule;
    }

    @GetMapping("/{rule_cd}/attributes")
    @Operation(summary = "List DQ rule attributes", description = "Returns attribute bindings for a DQ rule.")
    public List<DqRuleAttributeDto> findRuleAttributes(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Rule code.") @PathVariable("rule_cd") String ruleCode) {
        logger.debug("Listing DQ rule attributes. clientId={}, ruleCode={}", clientId, ruleCode);
        List<DqRuleAttributeDto> attributes = dqRuleService.findRuleAttributes(clientId, ruleCode);
        logger.debug("DQ rule attributes resolved. clientId={}, ruleCode={}, resultCount={}", clientId, ruleCode, attributes.size());
        return attributes;
    }

    @GetMapping("/results")
    @Operation(summary = "List DQ results", description = "Returns observed DQ results for a logical attribute.")
    public List<DqRuleResultDto> findRuleResults(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Logical attribute code.") @RequestParam("logical_attribute_cd") String logicalAttributeCode) {
        logger.debug("Listing DQ rule results. clientId={}, logicalAttributeCode={}", clientId, logicalAttributeCode);
        List<DqRuleResultDto> results = dqRuleService.findRuleResults(clientId, logicalAttributeCode);
        logger.debug("DQ rule results resolved. clientId={}, logicalAttributeCode={}, resultCount={}", clientId, logicalAttributeCode, results.size());
        return results;
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request DQ rules", description = "Creates workflow tasks for one or more requested DQ rules.")
    public List<WorkflowTaskResponseDto> requestRules(@Valid @RequestBody DqRuleRequestDto request) {
        logger.debug("Requesting DQ rules. clientId={}, ruleCount={}", request.client_id(), request.rule_names().size());
        List<WorkflowTaskResponseDto> tasks = dqRuleService.requestRules(request);
        logger.debug("DQ rules requested. clientId={}, workflowTaskCount={}", request.client_id(), tasks.size());
        return tasks;
    }

    @GetMapping("/requests/{workflow_task_id}")
    @Operation(summary = "Get DQ request", description = "Returns a DQ rule request workflow task for the supplied client.")
    public WorkflowTaskResponseDto findRequest(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Workflow task identifier.") @PathVariable("workflow_task_id") UUID workflowTaskId) {
        logger.debug("Fetching DQ rule request. clientId={}, workflowTaskId={}", clientId, workflowTaskId);
        WorkflowTaskResponseDto task = dqRuleService.findRequest(clientId, workflowTaskId);
        logger.debug("DQ rule request resolved. clientId={}, workflowTaskId={}", clientId, workflowTaskId);
        return task;
    }

    private static final class MissingDqRuleService implements DqRuleService {

        @Override
        public List<DqRuleCatalogDto> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
            throw new DqRuleServiceException("DqRuleService is not configured");
        }

        @Override
        public DqRuleCatalogDto findRule(String clientId, String ruleCode) {
            throw new DqRuleServiceException("DqRuleService is not configured");
        }

        @Override
        public List<DqRuleAttributeDto> findRuleAttributes(String clientId, String ruleCode) {
            throw new DqRuleServiceException("DqRuleService is not configured");
        }

        @Override
        public List<DqRuleResultDto> findRuleResults(String clientId, String logicalAttributeCode) {
            throw new DqRuleServiceException("DqRuleService is not configured");
        }

        @Override
        public DqRuleResultDto ingestResult(DqRuleResultIngestRequestDto request, String principalCd) {
            throw new DqRuleServiceException("DqRuleService is not configured");
        }

        @Override
        public List<WorkflowTaskResponseDto> requestRules(DqRuleRequestDto request) {
            throw new DqRuleServiceException("DqRuleService is not configured");
        }

        @Override
        public WorkflowTaskResponseDto findRequest(String clientId, UUID workflowTaskId) {
            throw new DqRuleServiceException("DqRuleService is not configured");
        }
    }
}
