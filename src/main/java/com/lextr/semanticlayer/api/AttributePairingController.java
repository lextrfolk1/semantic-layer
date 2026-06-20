package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.AttributePairingRegistrationRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingRegistrationResponseDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionResponseDto;
import com.lextr.semanticlayer.exception.AttributePairingRegistrationServiceException;
import com.lextr.semanticlayer.exception.AttributePairingResolutionServiceException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.service.AttributePairingRegistrationService;
import com.lextr.semanticlayer.service.AttributePairingResolutionService;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attribute-pairings")
@Tag(name = "Attribute Pairings", description = "Attribute pairing registration and resolution operations.")
public class AttributePairingController {

    private final AttributePairingRegistrationService attributePairingRegistrationService;
    private final AttributePairingResolutionService attributePairingResolutionService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    @Autowired
    public AttributePairingController(
            ObjectProvider<AttributePairingRegistrationService> attributePairingRegistrationServiceProvider,
            ObjectProvider<AttributePairingResolutionService> attributePairingResolutionServiceProvider,
            ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
            SQLQueryLoaderUtil sqlQueryLoaderUtil
    ) {
        this.attributePairingRegistrationService = attributePairingRegistrationServiceProvider.getIfAvailable(MissingAttributePairingRegistrationService::new);
        this.attributePairingResolutionService = attributePairingResolutionServiceProvider.getIfAvailable(MissingAttributePairingResolutionService::new);
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @GetMapping
    @Operation(summary = "List attribute pairings", description = "Returns all registered attribute pairings.")
    public List<Map<String, Object>> listPairings(
            @Parameter(description = "Client ID filter.") @RequestParam(value = "client_id", required = false) String clientId) {
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId);
        return jdbcTemplate.queryForList(
                sqlQueryLoaderUtil.getQuery("attribute_pairing.find_all"),
                params
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register attribute pairing", description = "Registers an attribute pairing for logical display-to-filter resolution.")
    public AttributePairingRegistrationResponseDto registerPairing(
            @Valid @RequestBody AttributePairingRegistrationRequestDto request) {
        return attributePairingRegistrationService.registerPairing(request);
    }

    @PostMapping("/resolve")
    @Operation(summary = "Resolve attribute pairing", description = "Resolves a display value to its filter value using the active pairing.")
    public AttributePairingResolutionResponseDto resolvePairing(
            @Valid @RequestBody AttributePairingResolutionRequestDto request) {
        return attributePairingResolutionService.resolvePairing(request);
    }

    private static final class MissingAttributePairingRegistrationService implements AttributePairingRegistrationService {

        @Override
        public AttributePairingRegistrationResponseDto registerPairing(AttributePairingRegistrationRequestDto request) {
            throw new AttributePairingRegistrationServiceException("AttributePairingRegistrationService is not configured");
        }
    }

    private static final class MissingAttributePairingResolutionService implements AttributePairingResolutionService {

        @Override
        public AttributePairingResolutionResponseDto resolvePairing(AttributePairingResolutionRequestDto request) {
            throw new AttributePairingResolutionServiceException("AttributePairingResolutionService is not configured");
        }
    }
}
