package com.lextr.semanticlayer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.AttributePairingResolutionDao;
import com.lextr.semanticlayer.dto.AttributePairingResolutionRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionResponseDto;
import com.lextr.semanticlayer.model.AttributePairingCacheHitWriteRequest;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheWriteRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributePairingResolutionServiceImplTest {

    @Test
    void resolvePopulatesCacheOnMiss() {
        RecordingAttributePairingResolutionDao dao = new RecordingAttributePairingResolutionDao();
        dao.activePairing = pairing("{\"Acme Corp\":\"CUST-100\"}");
        AttributePairingResolutionServiceImpl service = new AttributePairingResolutionServiceImpl(dao, new ObjectMapper());

        AttributePairingResolutionResponseDto response = service.resolvePairing(request());

        assertEquals(1, dao.upsertRequests.size());
        assertEquals("CUSTOMER_NAME_TO_ID", dao.upsertRequests.get(0).pairing_cd());
        assertEquals("Acme Corp", dao.upsertRequests.get(0).display_value_txt());
        assertEquals("CUST-100", dao.upsertRequests.get(0).filter_value_txt());
        assertEquals("CUST-100", response.filter_value_txt());
        assertEquals(false, response.cache_hit_flg());
    }

    @Test
    void returnsCachedValueAndRecordsHit() {
        RecordingAttributePairingResolutionDao dao = new RecordingAttributePairingResolutionDao();
        dao.activePairing = pairing("{\"Acme Corp\":\"CUST-100\"}");
        dao.cachedValue = new AttributePairingValueCacheRecord(
                501L,
                "CUSTOMER_NAME_TO_ID",
                "client-a",
                "Acme Corp",
                "CUST-100",
                false,
                2L,
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T11:15:30Z")
        );
        AttributePairingResolutionServiceImpl service = new AttributePairingResolutionServiceImpl(dao, new ObjectMapper());

        AttributePairingResolutionResponseDto response = service.resolvePairing(request());

        assertEquals(0, dao.upsertRequests.size());
        assertEquals(1, dao.cacheHitRequests.size());
        assertEquals("client-a", dao.cacheHitRequests.get(0).client_id());
        assertEquals("CUST-100", response.filter_value_txt());
        assertTrue(response.cache_hit_flg());
    }

    private static AttributePairingResolutionRequestDto request() {
        return new AttributePairingResolutionRequestDto(
                "client-a",
                "meta",
                "customer",
                "customer_nm",
                "Acme Corp"
        );
    }

    private static AttributePairingCatalogRecord pairing(String inlineMap) {
        return new AttributePairingCatalogRecord(
                101L,
                "CUSTOMER_NAME_TO_ID",
                "Customer Name To Id",
                "meta",
                "customer",
                "customer_nm",
                "customer_id",
                "DISPLAY_TO_FILTER",
                "CACHED_LOOKUP",
                inlineMap,
                null,
                true,
                3600,
                "ONE_TO_ONE",
                false,
                false,
                true,
                "BTREE",
                20,
                "Resolve customer name to id",
                "client-a",
                "ACTIVE",
                "PENDING",
                1,
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer"
        );
    }

    private static final class RecordingAttributePairingResolutionDao implements AttributePairingResolutionDao {

        private AttributePairingCatalogRecord activePairing;
        private AttributePairingValueCacheRecord cachedValue;
        private final List<AttributePairingValueCacheWriteRequest> upsertRequests = new ArrayList<>();
        private final List<AttributePairingCacheHitWriteRequest> cacheHitRequests = new ArrayList<>();

        @Override
        public Optional<AttributePairingCatalogRecord> findPairing(String clientId, String pairingCode) {
            return Optional.ofNullable(activePairing);
        }

        @Override
        public Optional<AttributePairingCatalogRecord> findActivePairing(String clientId,
                                                                         String schemaCode,
                                                                         String objectCode,
                                                                         String displayAttributeCode) {
            return Optional.ofNullable(activePairing);
        }

        @Override
        public boolean isAttributeIndexed(String schemaCode, String objectCode, String attributeCode) {
            return true;
        }

        @Override
        public Optional<AttributePairingValueCacheRecord> findCachedValue(String pairingCode,
                                                                          String clientId,
                                                                          String displayValue,
                                                                          OffsetDateTime asOfTimestamp) {
            return Optional.ofNullable(cachedValue);
        }

        @Override
        public AttributePairingValueCacheRecord upsertCachedValue(AttributePairingValueCacheWriteRequest request) {
            upsertRequests.add(request);
            return new AttributePairingValueCacheRecord(
                    601L,
                    request.pairing_cd(),
                    request.client_id(),
                    request.display_value_txt(),
                    request.filter_value_txt(),
                    request.is_one_to_many_flg(),
                    request.hit_count_nbr(),
                    request.last_hit_ts(),
                    request.cached_ts(),
                    request.expires_ts()
            );
        }

        @Override
        public AttributePairingValueCacheRecord recordCacheHit(AttributePairingCacheHitWriteRequest request) {
            cacheHitRequests.add(request);
            return cachedValue;
        }
    }
}
