package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;

import java.time.LocalDate;
import java.util.Optional;

public interface GovernancePolicyPresetReadDao {

    Optional<GovernancePolicyPresetRecord> findPolicyPreset(String policyCode, String policyScopeCode, LocalDate asOfDate);
}
