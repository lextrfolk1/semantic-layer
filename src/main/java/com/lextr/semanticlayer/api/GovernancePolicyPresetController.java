package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
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
public class GovernancePolicyPresetController {

    private final GovernancePolicyPresetReadService governancePolicyPresetReadService;

    public GovernancePolicyPresetController(GovernancePolicyPresetReadService governancePolicyPresetReadService) {
        this.governancePolicyPresetReadService = governancePolicyPresetReadService;
    }

    @GetMapping
    public List<GovernancePolicyPresetDto> findPolicyPresets(
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "policy_scope_cd", required = false) String policyScopeCode,
            @RequestParam(value = "as_of_dt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return governancePolicyPresetReadService.findPolicyPresets(clientId, policyScopeCode, asOfDate);
    }

    @GetMapping("/{policy_code}")
    public GovernancePolicyPresetDto findPolicyPreset(
            @RequestParam("client_id") String clientId,
            @PathVariable("policy_code") String policyCode,
            @RequestParam("policy_scope_cd") String policyScopeCode,
            @RequestParam(value = "as_of_dt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return governancePolicyPresetReadService.findPolicyPreset(clientId, policyCode, policyScopeCode, asOfDate);
    }
}
