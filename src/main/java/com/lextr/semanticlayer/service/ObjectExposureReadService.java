package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;

import java.util.List;
import java.util.UUID;

public interface ObjectExposureReadService {

    List<ObjectExposureSummaryDto> findObjects(String clientId,
                                               String actorId,
                                               String roleCode,
                                               String purposeCode,
                                               String schemaCode,
                                               String lifecycleStatusCode);

    ObjectExposureDetailDto findObject(String clientId,
                                       String actorId,
                                       String roleCode,
                                       String purposeCode,
                                       UUID objectId);
}
