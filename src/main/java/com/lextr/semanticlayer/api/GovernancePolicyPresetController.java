package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/governance/policy-presets")
@Tag(name = "Governance", description = "Governance policy preset read operations.")
public class GovernancePolicyPresetController {

    private final GovernancePolicyPresetReadService governancePolicyPresetReadService;

    public GovernancePolicyPresetController(GovernancePolicyPresetReadService governancePolicyPresetReadService) {
        this.governancePolicyPresetReadService = governancePolicyPresetReadService;
    }

    @GetMapping
    @Operation(summary = "List policy presets", description = "Returns effective governance policy presets for a client.")
    public List<GovernancePolicyPresetDto> findPolicyPresets(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional policy scope filter.") @RequestParam(value = "policy_scope_cd", required = false) String policyScopeCode,
            @Parameter(description = "Optional as-of date in ISO-8601 format.") @RequestParam(value = "as_of_dt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return governancePolicyPresetReadService.findPolicyPresets(clientId, policyScopeCode, asOfDate);
    }

    @GetMapping("/{policy_code}")
    @Operation(summary = "Get policy preset", description = "Returns one effective governance policy preset for a client and scope.")
    public GovernancePolicyPresetDto findPolicyPreset(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Policy code.") @PathVariable("policy_code") String policyCode,
            @Parameter(description = "Policy scope code.") @RequestParam("policy_scope_cd") String policyScopeCode,
            @Parameter(description = "Optional as-of date in ISO-8601 format.") @RequestParam(value = "as_of_dt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return governancePolicyPresetReadService.findPolicyPreset(clientId, policyCode, policyScopeCode, asOfDate);
    }
}
