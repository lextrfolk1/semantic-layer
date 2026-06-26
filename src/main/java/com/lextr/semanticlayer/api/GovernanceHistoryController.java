package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.GovernanceHistoryEventDto;
import com.lextr.semanticlayer.service.GovernanceHistoryReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/governance/history")
@Tag(name = "Governance", description = "Governance history read operations.")
public class GovernanceHistoryController {

    private final GovernanceHistoryReadService governanceHistoryReadService;

    public GovernanceHistoryController(GovernanceHistoryReadService governanceHistoryReadService) {
        this.governanceHistoryReadService = governanceHistoryReadService;
    }

    @GetMapping
    @Operation(summary = "List governance history", description = "Returns governance timeline events for one entity within a client scope.")
    public List<GovernanceHistoryEventDto> findHistory(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Entity type code.") @RequestParam("entity_type_cd") String entityTypeCode,
            @Parameter(description = "Entity reference.") @RequestParam("entity_ref") String entityRef,
            @Parameter(description = "Optional change type filter.") @RequestParam(value = "change_type_cd", required = false) String changeTypeCode) {
        return governanceHistoryReadService.findHistory(clientId, entityTypeCode, entityRef, changeTypeCode);
    }
}
