package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ObservabilitySignalCorrelationRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalIngestRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalResponseDto;

import java.util.List;

public interface ObservabilitySignalService {

    ObservabilitySignalResponseDto ingestSignal(ObservabilitySignalIngestRequestDto request);

    List<ObservabilitySignalResponseDto> findSignals(String clientId,
                                                     String signalTypeCode,
                                                     String severityCode,
                                                     String signalStatusCode,
                                                     String correlationKeyText);

    ObservabilitySignalResponseDto correlateSignal(Long signalId, ObservabilitySignalCorrelationRequestDto request);
}
