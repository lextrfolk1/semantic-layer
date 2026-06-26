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
        return observabilitySignalService.ingestSignal(request);
    }

    @GetMapping
    @Operation(summary = "List observability signals", description = "Returns observability signals visible for the supplied client.")
    public List<ObservabilitySignalResponseDto> findSignals(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional signal type filter.") @RequestParam(value = "signal_type_cd", required = false) String signalTypeCode,
            @Parameter(description = "Optional severity filter.") @RequestParam(value = "severity_cd", required = false) String severityCode,
            @Parameter(description = "Optional signal status filter.") @RequestParam(value = "signal_status_cd", required = false) String signalStatusCode,
            @Parameter(description = "Optional correlation key filter.") @RequestParam(value = "correlation_key_txt", required = false) String correlationKeyText) {
        return observabilitySignalService.findSignals(clientId, signalTypeCode, severityCode, signalStatusCode, correlationKeyText);
    }

    @PostMapping("/{signal_id}/correlate")
    @Operation(summary = "Correlate observability signal", description = "Updates an observability signal with correlation and routing details.")
    public ObservabilitySignalResponseDto correlateSignal(
            @Parameter(description = "Signal identifier.") @PathVariable("signal_id") Long signalId,
            @Valid @RequestBody ObservabilitySignalCorrelationRequestDto request) {
        return observabilitySignalService.correlateSignal(signalId, request);
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
