package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.AttributePairingRegistrationRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingRegistrationResponseDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionResponseDto;
import com.lextr.semanticlayer.exception.AttributePairingRegistrationServiceException;
import com.lextr.semanticlayer.exception.AttributePairingResolutionServiceException;
import com.lextr.semanticlayer.service.AttributePairingRegistrationService;
import com.lextr.semanticlayer.service.AttributePairingResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attribute-pairings")
@Tag(name = "Attribute Pairings", description = "Attribute pairing registration and resolution operations.")
public class AttributePairingController {

    private final AttributePairingRegistrationService attributePairingRegistrationService;
    private final AttributePairingResolutionService attributePairingResolutionService;

    @Autowired
    public AttributePairingController(
            ObjectProvider<AttributePairingRegistrationService> attributePairingRegistrationServiceProvider,
            ObjectProvider<AttributePairingResolutionService> attributePairingResolutionServiceProvider
    ) {
        this(
                attributePairingRegistrationServiceProvider.getIfAvailable(MissingAttributePairingRegistrationService::new),
                attributePairingResolutionServiceProvider.getIfAvailable(MissingAttributePairingResolutionService::new)
        );
    }

    AttributePairingController(AttributePairingRegistrationService attributePairingRegistrationService,
                               AttributePairingResolutionService attributePairingResolutionService) {
        this.attributePairingRegistrationService = attributePairingRegistrationService;
        this.attributePairingResolutionService = attributePairingResolutionService;
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
