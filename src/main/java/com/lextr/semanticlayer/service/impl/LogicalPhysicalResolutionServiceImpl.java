package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.LogicalPhysicalResolutionDao;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.LogicalPhysicalResolutionRecord;
import com.lextr.semanticlayer.service.LogicalPhysicalResolutionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogicalPhysicalResolutionServiceImpl implements LogicalPhysicalResolutionService {

    private final LogicalPhysicalResolutionDao logicalPhysicalResolutionDao;

    @Autowired
    public LogicalPhysicalResolutionServiceImpl(ObjectProvider<LogicalPhysicalResolutionDao> logicalPhysicalResolutionDaoProvider) {
        this(logicalPhysicalResolutionDaoProvider.getIfAvailable(MissingLogicalPhysicalResolutionDao::new));
    }

    LogicalPhysicalResolutionServiceImpl(LogicalPhysicalResolutionDao logicalPhysicalResolutionDao) {
        this.logicalPhysicalResolutionDao = logicalPhysicalResolutionDao;
    }

    @Override
    public List<LogicalPhysicalResolutionDto> resolveAttributes(String clientId,
                                                                String schemaCode,
                                                                String objectCode,
                                                                List<String> logicalAttributeCodes) {
        if (logicalAttributeCodes == null || logicalAttributeCodes.isEmpty()) {
            return List.of();
        }
        return logicalPhysicalResolutionDao.findByAttributes(clientId, schemaCode, objectCode, logicalAttributeCodes)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId, Long outboundId) {
        return logicalPhysicalResolutionDao.findByOutboundGrain(clientId, outboundId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private LogicalPhysicalResolutionDto toDto(LogicalPhysicalResolutionRecord record) {
        return new LogicalPhysicalResolutionDto(
                record.outbound_id(),
                record.outbound_cd(),
                record.grain_level_nbr(),
                record.client_id(),
                record.schema_cd(),
                record.object_cd(),
                record.logical_attribute_cd(),
                record.effective_logical_attribute_nm(),
                record.physical_attribute_nm(),
                record.source_object_nm(),
                record.engine_cd(),
                record.data_type_cd()
        );
    }

    private static final class MissingLogicalPhysicalResolutionDao implements LogicalPhysicalResolutionDao {

        @Override
        public List<LogicalPhysicalResolutionRecord> findByAttributes(String clientId,
                                                                      String schemaCode,
                                                                      String objectCode,
                                                                      List<String> logicalAttributeCodes) {
            throw new SemanticLayerException("LogicalPhysicalResolutionDao is not configured");
        }

        @Override
        public List<LogicalPhysicalResolutionRecord> findByOutboundGrain(String clientId, Long outboundId) {
            throw new SemanticLayerException("LogicalPhysicalResolutionDao is not configured");
        }
    }
}
