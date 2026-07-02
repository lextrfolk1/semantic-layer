package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.GovernanceHistoryReadDao;
import com.lextr.semanticlayer.dto.GovernanceHistoryEventDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.GovernanceHistoryEventRecord;
import com.lextr.semanticlayer.service.GovernanceHistoryReadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class GovernanceHistoryReadServiceImpl implements GovernanceHistoryReadService {

    private static final Logger logger = LoggerFactory.getLogger(GovernanceHistoryReadServiceImpl.class);

    private final GovernanceHistoryReadDao governanceHistoryReadDao;

    public GovernanceHistoryReadServiceImpl(GovernanceHistoryReadDao governanceHistoryReadDao) {
        this.governanceHistoryReadDao = governanceHistoryReadDao;
    }

    @Override
    public List<GovernanceHistoryEventDto> findHistory(String clientId,
                                                       String entityTypeCode,
                                                       String entityRef,
                                                       String changeTypeCode) {
        logger.debug("Finding governance history. clientId={}, entityTypeCode={}, entityRef={}, changeTypeCode={}",
                clientId, entityTypeCode, entityRef, changeTypeCode);
        validateRequired("client_id", clientId);
        validateRequired("entity_type_cd", entityTypeCode);
        validateRequired("entity_ref", entityRef);

        String resolvedEntityRef = entityRef;
        if ("RELATIONSHIP".equalsIgnoreCase(entityTypeCode) && entityRef != null && !entityRef.isBlank()) {
            try {
                UUID.fromString(entityRef);
            } catch (IllegalArgumentException e) {
                resolvedEntityRef = UUID.nameUUIDFromBytes(entityRef.getBytes(StandardCharsets.UTF_8)).toString();
                logger.debug("Resolved RELATIONSHIP entityRef {} to UUID {}", entityRef, resolvedEntityRef);
            }
        }

        List<GovernanceHistoryEventRecord> allEvents =
                governanceHistoryReadDao.findEvents(clientId, entityTypeCode, resolvedEntityRef, null);
        if (allEvents.isEmpty()) {
            logger.warn("Governance history not found. clientId={}, entityTypeCode={}, entityRef={}",
                    clientId, entityTypeCode, entityRef);
            throw new RegistryResourceNotFoundException("governance history", entityTypeCode + "/" + entityRef);
        }

        List<GovernanceHistoryEventRecord> filteredEvents = changeTypeCode == null || changeTypeCode.isBlank()
                ? allEvents
                : governanceHistoryReadDao.findEvents(clientId, entityTypeCode, resolvedEntityRef, changeTypeCode);
        List<GovernanceHistoryEventDto> history = filteredEvents.stream().map(this::toDto).toList();
        logger.debug("Governance history resolved. clientId={}, entityTypeCode={}, entityRef={}, resultCount={}",
                clientId, entityTypeCode, entityRef, history.size());
        return history;
    }

    private void validateRequired(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            logger.warn("Governance history validation failed. fieldName={}", fieldName);
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
