package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObservabilitySignalDao;
import com.lextr.semanticlayer.dto.ObservabilitySignalCorrelationRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalIngestRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalResponseDto;
import com.lextr.semanticlayer.exception.ObservabilitySignalServiceException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.ObservabilitySignalCorrelationWriteRequest;
import com.lextr.semanticlayer.model.ObservabilitySignalRecord;
import com.lextr.semanticlayer.model.ObservabilitySignalWriteRequest;
import com.lextr.semanticlayer.service.ObservabilitySignalService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ObservabilitySignalServiceImpl implements ObservabilitySignalService {

    private static final String DEFAULT_SIGNAL_STATUS_CD = "OPEN";
    private static final String DEFAULT_SEVERITY_CD = "INFO";

    private final ObservabilitySignalDao observabilitySignalDao;

    @Autowired
    public ObservabilitySignalServiceImpl(ObjectProvider<ObservabilitySignalDao> observabilitySignalDaoProvider) {
        this(observabilitySignalDaoProvider.getIfAvailable(MissingObservabilitySignalDao::new));
    }

    ObservabilitySignalServiceImpl(ObservabilitySignalDao observabilitySignalDao) {
        this.observabilitySignalDao = observabilitySignalDao;
    }

    @Override
    public ObservabilitySignalResponseDto ingestSignal(ObservabilitySignalIngestRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ObservabilitySignalRecord record = observabilitySignalDao.insertSignal(new ObservabilitySignalWriteRequest(
                request.client_id(),
                request.signal_type_cd(),
                defaultText(request.severity_cd(), DEFAULT_SEVERITY_CD),
                defaultText(request.signal_status_cd(), DEFAULT_SIGNAL_STATUS_CD),
                request.source_system_cd(),
                request.source_entity_type_cd(),
                request.source_entity_ref_txt(),
                request.correlation_key_txt(),
                request.finding_summary_txt(),
                request.finding_detail_txt(),
                request.detected_ts(),
                request.dq_rerun_requested_flg(),
                request.dq_rerun_reason_txt(),
                now,
                request.reported_by(),
                now,
                request.reported_by()
        ));
        return toDto(record);
    }

    @Override
    public List<ObservabilitySignalResponseDto> findSignals(String clientId,
                                                            String signalTypeCode,
                                                            String severityCode,
                                                            String signalStatusCode,
                                                            String correlationKeyText) {
        return observabilitySignalDao.findSignals(clientId, signalTypeCode, severityCode, signalStatusCode, correlationKeyText)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public ObservabilitySignalResponseDto correlateSignal(Long signalId, ObservabilitySignalCorrelationRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ObservabilitySignalRecord record = observabilitySignalDao.correlateSignal(
                new ObservabilitySignalCorrelationWriteRequest(
                        signalId,
                        request.client_id(),
                        request.signal_status_cd(),
                        request.workflow_task_id(),
                        request.dq_rerun_requested_flg(),
                        request.dq_rerun_reason_txt(),
                        request.acknowledged_ts(),
                        request.resolved_ts(),
                        now,
                        request.correlated_by()
                )
        ).orElseThrow(() -> new RegistryResourceNotFoundException("observability signal", String.valueOf(signalId)));
        return toDto(record);
    }

    private ObservabilitySignalResponseDto toDto(ObservabilitySignalRecord record) {
        return new ObservabilitySignalResponseDto(
                record.id(),
                record.client_id(),
                record.signal_type_cd(),
                record.severity_cd(),
                record.signal_status_cd(),
                record.source_system_cd(),
                record.source_entity_type_cd(),
                record.source_entity_ref_txt(),
                record.correlation_key_txt(),
                record.finding_summary_txt(),
                record.finding_detail_txt(),
                record.detected_ts(),
                record.acknowledged_ts(),
                record.resolved_ts(),
                record.workflow_task_id(),
                record.dq_rerun_requested_flg(),
                record.dq_rerun_reason_txt(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static final class MissingObservabilitySignalDao implements ObservabilitySignalDao {

        @Override
        public ObservabilitySignalRecord insertSignal(ObservabilitySignalWriteRequest request) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalDao is not configured");
        }

        @Override
        public List<ObservabilitySignalRecord> findSignals(String clientId,
                                                           String signalTypeCode,
                                                           String severityCode,
                                                           String signalStatusCode,
                                                           String correlationKeyText) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalDao is not configured");
        }

        @Override
        public java.util.Optional<ObservabilitySignalRecord> correlateSignal(ObservabilitySignalCorrelationWriteRequest request) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalDao is not configured");
        }
    }
}
