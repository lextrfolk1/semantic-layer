package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.GovernanceHistoryReadDao;
import com.lextr.semanticlayer.dto.GovernanceHistoryEventDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.GovernanceHistoryEventRecord;
import com.lextr.semanticlayer.service.GovernanceHistoryReadService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GovernanceHistoryReadServiceImpl implements GovernanceHistoryReadService {

    private final GovernanceHistoryReadDao governanceHistoryReadDao;

    public GovernanceHistoryReadServiceImpl(GovernanceHistoryReadDao governanceHistoryReadDao) {
        this.governanceHistoryReadDao = governanceHistoryReadDao;
    }

    @Override
    public List<GovernanceHistoryEventDto> findHistory(String clientId,
                                                       String entityTypeCode,
                                                       String entityRef,
                                                       String changeTypeCode) {
        validateRequired("client_id", clientId);
        validateRequired("entity_type_cd", entityTypeCode);
        validateRequired("entity_ref", entityRef);

        List<GovernanceHistoryEventRecord> allEvents =
                governanceHistoryReadDao.findEvents(clientId, entityTypeCode, entityRef, null);
        if (allEvents.isEmpty()) {
            throw new RegistryResourceNotFoundException("governance history", entityTypeCode + "/" + entityRef);
        }

        List<GovernanceHistoryEventRecord> filteredEvents = changeTypeCode == null || changeTypeCode.isBlank()
                ? allEvents
                : governanceHistoryReadDao.findEvents(clientId, entityTypeCode, entityRef, changeTypeCode);
        return filteredEvents.stream().map(this::toDto).toList();
    }

    private void validateRequired(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new PolicyViolationException(fieldName.toUpperCase() + "_REQUIRED", fieldName + " is required");
        }
    }

    private GovernanceHistoryEventDto toDto(GovernanceHistoryEventRecord record) {
        return new GovernanceHistoryEventDto(
                record.event_id(),
                record.client_id(),
                record.entity_type_cd(),
                record.entity_ref(),
                record.change_type_cd(),
                record.change_summary_txt(),
                record.actor_id(),
                record.event_ts(),
                record.old_value_json(),
                record.new_value_json()
        );
    }
}
