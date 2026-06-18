package com.lextr.semanticlayer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.AttributePairingResolutionDao;
import com.lextr.semanticlayer.dto.AttributePairingResolutionRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionResponseDto;
import com.lextr.semanticlayer.exception.AttributePairingResolutionServiceException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributePairingCacheHitWriteRequest;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheWriteRequest;
import com.lextr.semanticlayer.service.AttributePairingResolutionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AttributePairingResolutionServiceImpl implements AttributePairingResolutionService {

    private final AttributePairingResolutionDao attributePairingResolutionDao;
    private final ObjectMapper objectMapper;

    @Autowired
    public AttributePairingResolutionServiceImpl(
            ObjectProvider<AttributePairingResolutionDao> attributePairingResolutionDaoProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        this(
                attributePairingResolutionDaoProvider.getIfAvailable(MissingAttributePairingResolutionDao::new),
                objectMapperProvider.getIfAvailable(ObjectMapper::new)
        );
    }

    AttributePairingResolutionServiceImpl(AttributePairingResolutionDao attributePairingResolutionDao,
                                          ObjectMapper objectMapper) {
        this.attributePairingResolutionDao = attributePairingResolutionDao;
        this.objectMapper = objectMapper;
    }

    @Override
    public AttributePairingResolutionResponseDto resolvePairing(AttributePairingResolutionRequestDto request) {
        AttributePairingCatalogRecord pairing = attributePairingResolutionDao.findActivePairing(
                        request.client_id(),
                        request.schema_cd(),
                        request.object_cd(),
                        request.display_attribute_cd()
                )
                .orElseThrow(() -> new RegistryResourceNotFoundException(
                        "attribute pairing",
                        request.schema_cd() + "." + request.object_cd() + "." + request.display_attribute_cd()
                ));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Optional<AttributePairingValueCacheRecord> cachedValue = attributePairingResolutionDao.findCachedValue(
                pairing.pairing_cd(),
                request.client_id(),
                request.display_value_txt(),
                now
        );
        if (cachedValue.isPresent()) {
            AttributePairingValueCacheRecord cacheRecord = cachedValue.get();
            attributePairingResolutionDao.recordCacheHit(new AttributePairingCacheHitWriteRequest(
                    cacheRecord.pairing_cd(),
                    cacheRecord.display_value_txt(),
                    cacheRecord.client_id(),
                    now
            ));
            return toResponse(pairing, cacheRecord, true);
        }

        ResolvedValue resolvedValue = resolveValueFromInlineMap(pairing, request.display_value_txt());
        AttributePairingValueCacheRecord cacheRecord = attributePairingResolutionDao.upsertCachedValue(
                new AttributePairingValueCacheWriteRequest(
                        pairing.pairing_cd(),
                        request.client_id(),
                        request.display_value_txt(),
                        resolvedValue.filterValue(),
                        resolvedValue.oneToMany(),
                        0L,
                        null,
                        now,
                        pairing.lookup_cache_enabled_flg()
                                ? now.plusSeconds(pairing.lookup_cache_ttl_seconds_nbr() == null ? 3600 : pairing.lookup_cache_ttl_seconds_nbr())
                                : null
                )
        );
        return toResponse(pairing, cacheRecord, false);
    }

    private ResolvedValue resolveValueFromInlineMap(AttributePairingCatalogRecord pairing, String displayValue) {
        if (pairing.lookup_inline_map_jsonb() == null || pairing.lookup_inline_map_jsonb().isBlank()) {
            throw new AttributePairingResolutionServiceException(
                    "Attribute pairing " + pairing.pairing_cd() + " does not define an inline lookup map"
            );
        }

        try {
            Map<String, Object> valueMap = objectMapper.readValue(
                    pairing.lookup_inline_map_jsonb(),
                    new TypeReference<>() {
                    }
            );
            Object resolved = valueMap.get(displayValue);
            if (resolved == null) {
                throw new RegistryResourceNotFoundException("attribute pairing value", displayValue);
            }
            if (resolved instanceof List<?> list) {
                return new ResolvedValue(objectMapper.writeValueAsString(list), true);
            }
            return new ResolvedValue(String.valueOf(resolved), false);
        } catch (JsonProcessingException exception) {
            throw new AttributePairingResolutionServiceException(
                    "Unable to resolve attribute pairing " + pairing.pairing_cd() + " from inline map",
                    exception
            );
        }
    }

    private AttributePairingResolutionResponseDto toResponse(AttributePairingCatalogRecord pairing,
                                                             AttributePairingValueCacheRecord cacheRecord,
                                                             boolean cacheHit) {
        return new AttributePairingResolutionResponseDto(
                pairing.pairing_cd(),
                pairing.schema_cd(),
                pairing.object_cd(),
                pairing.display_attribute_cd(),
                pairing.filter_attribute_cd(),
                cacheRecord.display_value_txt(),
                cacheRecord.filter_value_txt(),
                cacheRecord.is_one_to_many_flg(),
                cacheHit
        );
    }

    private record ResolvedValue(String filterValue, boolean oneToMany) {
    }

    private static final class MissingAttributePairingResolutionDao implements AttributePairingResolutionDao {

        @Override
        public Optional<AttributePairingCatalogRecord> findPairing(String clientId, String pairingCode) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public Optional<AttributePairingCatalogRecord> findActivePairing(String clientId,
                                                                         String schemaCode,
                                                                         String objectCode,
                                                                         String displayAttributeCode) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public boolean isAttributeIndexed(String schemaCode, String objectCode, String attributeCode) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public Optional<AttributePairingValueCacheRecord> findCachedValue(String pairingCode,
                                                                          String clientId,
                                                                          String displayValue,
                                                                          OffsetDateTime asOfTimestamp) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public AttributePairingValueCacheRecord upsertCachedValue(AttributePairingValueCacheWriteRequest request) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public AttributePairingValueCacheRecord recordCacheHit(AttributePairingCacheHitWriteRequest request) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }
    }
}
