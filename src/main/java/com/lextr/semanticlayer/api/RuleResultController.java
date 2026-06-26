package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ExternalRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.RuleResultIngestResponseDto;
import com.lextr.semanticlayer.exception.RuleResultServiceException;
import com.lextr.semanticlayer.service.RuleResultService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rule-results")
public class RuleResultController {

    private final RuleResultService ruleResultService;

    @Autowired
    public RuleResultController(ObjectProvider<RuleResultService> ruleResultServiceProvider) {
        this(ruleResultServiceProvider.getIfAvailable(MissingRuleResultService::new));
    }

    RuleResultController(RuleResultService ruleResultService) {
        this.ruleResultService = ruleResultService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest rule result", description = "Stores external rule results and routes editcheck outputs to LP-24.")
    public RuleResultIngestResponseDto ingestRuleResult(@Valid @RequestBody ExternalRuleResultIngestRequestDto request,
                                                        @RequestHeader(value = "X-Principal-Cd", required = false) String principalCd) {
        return ruleResultService.ingestRuleResult(request, principalCd);
    }

    private static final class MissingRuleResultService implements RuleResultService {

        @Override
        public RuleResultIngestResponseDto ingestRuleResult(ExternalRuleResultIngestRequestDto request, String principalCd) {
            throw new RuleResultServiceException("RuleResultService is not configured");
        }
    }
}
