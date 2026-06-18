package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.AttributePairingCacheHitWriteRequest;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheWriteRequest;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface AttributePairingResolutionDao {

    Optional<AttributePairingCatalogRecord> findPairing(String clientId, String pairingCode);

    Optional<AttributePairingCatalogRecord> findActivePairing(String clientId,
                                                              String schemaCode,
                                                              String objectCode,
                                                              String displayAttributeCode);

    boolean isAttributeIndexed(String schemaCode, String objectCode, String attributeCode);

    Optional<AttributePairingValueCacheRecord> findCachedValue(String pairingCode,
                                                               String clientId,
                                                               String displayValue,
                                                               OffsetDateTime asOfTimestamp);

    AttributePairingValueCacheRecord upsertCachedValue(AttributePairingValueCacheWriteRequest request);

    AttributePairingValueCacheRecord recordCacheHit(AttributePairingCacheHitWriteRequest request);
}
