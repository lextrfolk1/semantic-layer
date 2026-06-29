package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ObservabilitySignalCorrelationRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalIngestRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalResponseDto;
import com.lextr.semanticlayer.exception.ObservabilitySignalServiceException;
import com.lextr.semanticlayer.service.ObservabilitySignalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/observability-signals")
@Tag(name = "Observability Signals", description = "Data observability signal ingest, correlation, and screen operations.")
public class ObservabilitySignalController {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilitySignalController.class);

    private final ObservabilitySignalService observabilitySignalService;

    @Autowired
    public ObservabilitySignalController(ObjectProvider<ObservabilitySignalService> observabilitySignalServiceProvider) {
        this(observabilitySignalServiceProvider.getIfAvailable(MissingObservabilitySignalService::new));
    }

    ObservabilitySignalController(ObservabilitySignalService observabilitySignalService) {
        this.observabilitySignalService = observabilitySignalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest observability signal", description = "Registers an observability signal for later correlation.")
    public ObservabilitySignalResponseDto ingestSignal(@Valid @RequestBody ObservabilitySignalIngestRequestDto request) {
        logger.debug(
                "Ingesting observability signal. clientId={}, signalTypeCode={}, severityCode={}, sourceSystemCode={}, rerunRequested={}",
                request.client_id(),
                request.signal_type_cd(),
                request.severity_cd(),
                request.source_system_cd(),
                request.dq_rerun_requested_flg()
        );
        ObservabilitySignalResponseDto response = observabilitySignalService.ingestSignal(request);
        logger.debug("Observability signal ingested. clientId={}, signalId={}", request.client_id(), response.id());
        return response;
    }

    @GetMapping
    @Operation(summary = "List observability signals", description = "Returns observability signals visible for the supplied client.")
    public List<ObservabilitySignalResponseDto> findSignals(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional signal type filter.") @RequestParam(value = "signal_type_cd", required = false) String signalTypeCode,
            @Parameter(description = "Optional severity filter.") @RequestParam(value = "severity_cd", required = false) String severityCode,
            @Parameter(description = "Optional signal status filter.") @RequestParam(value = "signal_status_cd", required = false) String signalStatusCode,
            @Parameter(description = "Optional correlation key filter.") @RequestParam(value = "correlation_key_txt", required = false) String correlationKeyText) {
        logger.debug(
                "Listing observability signals. clientId={}, signalTypeCode={}, severityCode={}, signalStatusCode={}, correlationKeyProvided={}",
                clientId,
                signalTypeCode,
                severityCode,
                signalStatusCode,
                correlationKeyText != null && !correlationKeyText.isBlank()
        );
        List<ObservabilitySignalResponseDto> signals =
                observabilitySignalService.findSignals(clientId, signalTypeCode, severityCode, signalStatusCode, correlationKeyText);
        logger.debug("Observability signals resolved. clientId={}, resultCount={}", clientId, signals.size());
        return signals;
    }

    @PostMapping("/{signal_id}/correlate")
    @Operation(summary = "Correlate observability signal", description = "Updates an observability signal with correlation and routing details.")
    public ObservabilitySignalResponseDto correlateSignal(
            @Parameter(description = "Signal identifier.") @PathVariable("signal_id") Long signalId,
            @Valid @RequestBody ObservabilitySignalCorrelationRequestDto request) {
        logger.debug(
                "Correlating observability signal. clientId={}, signalId={}, signalStatusCode={}, workflowTaskId={}, rerunRequested={}",
                request.client_id(),
                signalId,
                request.signal_status_cd(),
                request.workflow_task_id(),
                request.dq_rerun_requested_flg()
        );
        ObservabilitySignalResponseDto response = observabilitySignalService.correlateSignal(signalId, request);
        logger.debug("Observability signal correlated. clientId={}, signalId={}, signalStatusCode={}",
                request.client_id(), signalId, response.signal_status_cd());
        return response;
    }

    private static final class MissingObservabilitySignalService implements ObservabilitySignalService {

        @Override
        public ObservabilitySignalResponseDto ingestSignal(ObservabilitySignalIngestRequestDto request) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalService is not configured");
        }

        @Override
        public List<ObservabilitySignalResponseDto> findSignals(String clientId,
                                                                String signalTypeCode,
                                                                String severityCode,
                                                                String signalStatusCode,
                                                                String correlationKeyText) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalService is not configured");
        }

        @Override
        public ObservabilitySignalResponseDto correlateSignal(Long signalId, ObservabilitySignalCorrelationRequestDto request) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalService is not configured");
        }
    }
}
