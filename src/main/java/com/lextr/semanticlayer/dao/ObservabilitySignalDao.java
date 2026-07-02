package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.ObservabilitySignalCorrelationWriteRequest;
import com.lextr.semanticlayer.model.ObservabilitySignalRecord;
import com.lextr.semanticlayer.model.ObservabilitySignalWriteRequest;

import java.util.List;
import java.util.Optional;

public interface ObservabilitySignalDao {

    ObservabilitySignalRecord insertSignal(ObservabilitySignalWriteRequest request);

    List<ObservabilitySignalRecord> findSignals(String clientId,
                                                String signalTypeCode,
                                                String severityCode,
                                                String signalStatusCode,
                                                String correlationKeyText);

    Optional<ObservabilitySignalRecord> correlateSignal(ObservabilitySignalCorrelationWriteRequest request);
}
