package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;

import java.time.LocalDate;
import java.util.List;

public interface GovernancePolicyPresetReadService {

    List<GovernancePolicyPresetDto> findPolicyPresets(String clientId, String policyScopeCode, LocalDate asOfDate);

    GovernancePolicyPresetDto findPolicyPreset(String clientId, String policyCode, String policyScopeCode, LocalDate asOfDate);
}
