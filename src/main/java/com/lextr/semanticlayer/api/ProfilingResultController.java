package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ProfilingResultDto;
import com.lextr.semanticlayer.service.ProfilingResultReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiling")
@Tag(name = "Profiling", description = "Data profiling read operations.")
public class ProfilingResultController {

    private static final Logger logger = LoggerFactory.getLogger(ProfilingResultController.class);

    private final ProfilingResultReadService profilingResultReadService;

    public ProfilingResultController(ProfilingResultReadService profilingResultReadService) {
        this.profilingResultReadService = profilingResultReadService;
    }

    @GetMapping("/metrics")
    @Operation(summary = "List profiling metrics", description = "Returns profiling metrics for one object within the supplied client.")
    public List<ProfilingResultDto> findMetrics(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Object identifier.") @RequestParam("object_id") UUID objectId,
            @Parameter(description = "Optional profiling status filter.") @RequestParam(value = "profiling_status_cd", required = false) String profilingStatusCode) {
        logger.debug(
                "Listing profiling metrics. clientId={}, objectId={}, profilingStatusCode={}",
                clientId,
                objectId,
                profilingStatusCode
        );
        List<ProfilingResultDto> metrics = profilingResultReadService.findMetrics(clientId, objectId, profilingStatusCode);
        logger.debug("Profiling metrics resolved. clientId={}, objectId={}, resultCount={}", clientId, objectId, metrics.size());
        return metrics;
    }
}
