package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ProfilingResultDto;

import java.util.List;
import java.util.UUID;

public interface ProfilingResultReadService {

    List<ProfilingResultDto> findMetrics(String clientId, UUID objectId, String profilingStatusCode);
}
