package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.LogicalPhysicalResolutionDao;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.LogicalPhysicalResolutionRecord;
import com.lextr.semanticlayer.service.LogicalPhysicalResolutionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogicalPhysicalResolutionServiceImpl implements LogicalPhysicalResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(LogicalPhysicalResolutionServiceImpl.class);

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
        logger.debug(
                "Resolving logical attributes in service. clientId={}, schemaCode={}, objectCode={}, logicalAttributeCount={}",
                clientId,
                schemaCode,
                objectCode,
                logicalAttributeCodes == null ? 0 : logicalAttributeCodes.size()
        );
        if (logicalAttributeCodes == null || logicalAttributeCodes.isEmpty()) {
            logger.warn("Skipping logical attribute resolution because no attribute codes were supplied. clientId={}, schemaCode={}, objectCode={}", clientId, schemaCode, objectCode);
            return List.of();
        }
        List<LogicalPhysicalResolutionDto> resolutions =
                logicalPhysicalResolutionDao.findByAttributes(clientId, schemaCode, objectCode, logicalAttributeCodes)
                .stream()
                .map(this::toDto)
                .toList();
        logger.debug("Logical attributes resolved in service. clientId={}, schemaCode={}, objectCode={}, resultCount={}", clientId, schemaCode, objectCode, resolutions.size());
        return resolutions;
    }

    @Override
    public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId, Long outboundId) {
        logger.debug("Resolving outbound grain in service. clientId={}, outboundId={}", clientId, outboundId);
        List<LogicalPhysicalResolutionDto> resolutions = logicalPhysicalResolutionDao.findByOutboundGrain(clientId, outboundId)
                .stream()
                .map(this::toDto)
                .toList();
        logger.debug("Outbound grain resolved in service. clientId={}, outboundId={}, resultCount={}", clientId, outboundId, resolutions.size());
        return resolutions;
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
                record.data_type_cd(),
                false
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
