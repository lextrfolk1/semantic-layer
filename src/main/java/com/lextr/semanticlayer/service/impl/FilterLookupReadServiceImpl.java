package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupReadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class FilterLookupReadServiceImpl implements FilterLookupReadService {

    private static final Logger logger = LoggerFactory.getLogger(FilterLookupReadServiceImpl.class);

    private static final String GOVERNANCE_POLICY_CD = "GOV-FL-001";
    private static final String POLICY_SCOPE_CD = "FILTER_LOOKUP";
    private static final String GOV_DEFAULT = "GOV_DEFAULT";
    private static final String LOOKUP_OVERRIDE = "LOOKUP_OVERRIDE";
    private static final String GOV_ENFORCED = "GOV_ENFORCED";

    private final FilterLookupReadDao filterLookupReadDao;
    private final GovernancePolicyPresetReadDao governancePolicyPresetReadDao;

    public FilterLookupReadServiceImpl(FilterLookupReadDao filterLookupReadDao,
                                       GovernancePolicyPresetReadDao governancePolicyPresetReadDao) {
        this.filterLookupReadDao = filterLookupReadDao;
        this.governancePolicyPresetReadDao = governancePolicyPresetReadDao;
    }

    @Override
    public List<FilterLookupEffectiveReviewDto> findLookups(String clientId,
                                                            String governanceStatusCode,
                                                            String healthStatusCode,
                                                            String lifecycleStatusCode) {
        logger.debug(
                "Finding filter lookups. clientId={}, governanceStatusCode={}, healthStatusCode={}, lifecycleStatusCode={}",
                clientId,
                governanceStatusCode,
                healthStatusCode,
                lifecycleStatusCode
        );
        int reviewPeriodFloorDays = findReviewPeriodFloorDays();
        List<FilterLookupEffectiveReviewDto> lookups = filterLookupReadDao.findLookups(clientId, governanceStatusCode, healthStatusCode, lifecycleStatusCode).stream()
                .map(record -> toDto(record, reviewPeriodFloorDays, filterLookupReadDao.countValues(clientId, record.lookup_cd())))
                .toList();
        logger.debug("Filter lookups resolved. clientId={}, resultCount={}", clientId, lookups.size());
        return lookups;
    }

    @Override
    public FilterLookupEffectiveReviewDto findLookup(String clientId, String lookupCode) {
        logger.debug("Finding filter lookup. clientId={}, lookupCode={}", clientId, lookupCode);
        int reviewPeriodFloorDays = findReviewPeriodFloorDays();
        SemanticFilterLookupRecord record = filterLookupReadDao.findLookup(clientId, lookupCode)
                .orElseThrow(() -> new RegistryResourceNotFoundException("filter lookup", lookupCode));
        FilterLookupEffectiveReviewDto lookup =
                toDto(record, reviewPeriodFloorDays, filterLookupReadDao.countValues(clientId, lookupCode));
        logger.debug("Filter lookup resolved. clientId={}, lookupCode={}, governanceStatusCode={}",
                clientId, lookupCode, lookup.governance_status_cd());
        return lookup;
    }

    private int findReviewPeriodFloorDays() {
        GovernancePolicyPresetRecord policy = governancePolicyPresetReadDao.findPolicyPreset(
                        GOVERNANCE_POLICY_CD,
                        POLICY_SCOPE_CD,
                        LocalDate.now(ZoneOffset.UTC)
                )
                .orElseThrow(() -> new SemanticLayerException(
                        "Unable to resolve governance policy " + GOVERNANCE_POLICY_CD
                ));
        try {
            int reviewPeriodFloorDays = Integer.parseInt(policy.default_value_txt());
            logger.debug("Resolved filter lookup review period floor. policyCode={}, reviewPeriodFloorDays={}",
                    policy.policy_cd(), reviewPeriodFloorDays);
            return reviewPeriodFloorDays;
        } catch (RuntimeException exception) {
            logger.error("Failed to parse filter lookup governance policy. policyCode={}, errorMessage={}",
                    policy.policy_cd(), exception.getMessage(), exception);
            throw new SemanticLayerException(
                    "Unable to parse governance policy value for " + policy.policy_cd(),
                    exception
            );
        }
    }

    private FilterLookupEffectiveReviewDto toDto(SemanticFilterLookupRecord record,
                                                 int reviewPeriodFloorDays,
                                                 long valueCount) {
        Integer override = record.review_period_days_override();
        return new FilterLookupEffectiveReviewDto(
                record.id(),
                record.lookup_cd(),
                record.construction_type_cd(),
                override,
                effectiveReviewPeriodDays(override, reviewPeriodFloorDays),
                effectiveReviewPeriodSourceCode(override, reviewPeriodFloorDays),
                record.governance_status_cd(),
                record.health_status_cd(),
                valueCount,
                record.next_review_due_dt(),
                record.lifecycle_status_cd(),
                record.last_certified_ts(),
                record.last_certified_by(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private Integer effectiveReviewPeriodDays(Integer override, int reviewPeriodFloorDays) {
        if (override == null || override > reviewPeriodFloorDays) {
            return reviewPeriodFloorDays;
        }
        return override;
    }

    private String effectiveReviewPeriodSourceCode(Integer override, int reviewPeriodFloorDays) {
        if (override == null) {
            return GOV_DEFAULT;
        }
        if (override > reviewPeriodFloorDays) {
            return GOV_ENFORCED;
        }
        return LOOKUP_OVERRIDE;
    }
}
