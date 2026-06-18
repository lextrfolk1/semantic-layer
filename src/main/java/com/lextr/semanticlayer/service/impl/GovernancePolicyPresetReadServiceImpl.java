package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.exception.GovernancePolicyPresetNotFoundException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class GovernancePolicyPresetReadServiceImpl implements GovernancePolicyPresetReadService {

    private final GovernancePolicyPresetReadDao governancePolicyPresetReadDao;

    public GovernancePolicyPresetReadServiceImpl(GovernancePolicyPresetReadDao governancePolicyPresetReadDao) {
        this.governancePolicyPresetReadDao = governancePolicyPresetReadDao;
    }

    @Override
    public List<GovernancePolicyPresetDto> findPolicyPresets(String clientId, String policyScopeCode, LocalDate asOfDate) {
        validateClientId(clientId);
        LocalDate effectiveAsOfDate = asOfDate != null ? asOfDate : LocalDate.now(ZoneOffset.UTC);
        return governancePolicyPresetReadDao.findPolicyPresets(policyScopeCode, effectiveAsOfDate).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public GovernancePolicyPresetDto findPolicyPreset(String clientId, String policyCode, String policyScopeCode, LocalDate asOfDate) {
        validateClientId(clientId);
        LocalDate effectiveAsOfDate = asOfDate != null ? asOfDate : LocalDate.now(ZoneOffset.UTC);
        return governancePolicyPresetReadDao.findPolicyPreset(policyCode, policyScopeCode, effectiveAsOfDate)
                .map(this::toDto)
                .orElseThrow(() -> new GovernancePolicyPresetNotFoundException(policyCode));
    }

    private void validateClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new PolicyViolationException("CLIENT_ID_REQUIRED", "client_id is required");
        }
    }

    private GovernancePolicyPresetDto toDto(GovernancePolicyPresetRecord record) {
        return new GovernancePolicyPresetDto(
                record.policy_cd(),
                record.policy_nm(),
                record.policy_scope_cd(),
                record.default_value_txt(),
                record.data_type_cd(),
                record.is_overrideable_flg(),
                record.override_requires_approval_flg(),
                record.effective_from_dt(),
                record.effective_to_dt(),
                record.approved_by(),
                record.approved_ts(),
                record.created_ts(),
                record.created_by()
        );
    }
}
