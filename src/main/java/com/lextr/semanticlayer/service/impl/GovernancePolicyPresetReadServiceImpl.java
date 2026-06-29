package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.exception.GovernancePolicyPresetNotFoundException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class GovernancePolicyPresetReadServiceImpl implements GovernancePolicyPresetReadService {

    private static final Logger logger = LoggerFactory.getLogger(GovernancePolicyPresetReadServiceImpl.class);

    private final GovernancePolicyPresetReadDao governancePolicyPresetReadDao;

    public GovernancePolicyPresetReadServiceImpl(GovernancePolicyPresetReadDao governancePolicyPresetReadDao) {
        this.governancePolicyPresetReadDao = governancePolicyPresetReadDao;
    }

    @Override
    public List<GovernancePolicyPresetDto> findPolicyPresets(String clientId, String policyScopeCode, LocalDate asOfDate) {
        validateClientId(clientId);
        LocalDate effectiveAsOfDate = asOfDate != null ? asOfDate : LocalDate.now(ZoneOffset.UTC);
        logger.debug("Finding governance policy presets. clientId={}, policyScopeCode={}, asOfDate={}",
                clientId, policyScopeCode, effectiveAsOfDate);
        List<GovernancePolicyPresetDto> presets = governancePolicyPresetReadDao.findPolicyPresets(policyScopeCode, effectiveAsOfDate).stream()
                .map(this::toDto)
                .toList();
        logger.debug("Governance policy presets resolved. clientId={}, policyScopeCode={}, resultCount={}",
                clientId, policyScopeCode, presets.size());
        return presets;
    }

    @Override
    public GovernancePolicyPresetDto findPolicyPreset(String clientId, String policyCode, String policyScopeCode, LocalDate asOfDate) {
        validateClientId(clientId);
        LocalDate effectiveAsOfDate = asOfDate != null ? asOfDate : LocalDate.now(ZoneOffset.UTC);
        logger.debug("Finding governance policy preset. clientId={}, policyCode={}, policyScopeCode={}, asOfDate={}",
                clientId, policyCode, policyScopeCode, effectiveAsOfDate);
        GovernancePolicyPresetDto preset = governancePolicyPresetReadDao.findPolicyPreset(policyCode, policyScopeCode, effectiveAsOfDate)
                .map(this::toDto)
                .orElseThrow(() -> new GovernancePolicyPresetNotFoundException(policyCode));
        logger.debug("Governance policy preset resolved. clientId={}, policyCode={}, policyScopeCode={}",
                clientId, policyCode, policyScopeCode);
        return preset;
    }

    private void validateClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            logger.warn("Governance policy preset validation failed because clientId is missing.");
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
