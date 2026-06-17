package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObjectExposureReadDao {

    List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode);

    Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId);

    List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId);
}
