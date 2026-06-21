package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.service.RelationshipRegistrationService;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/relationships")
@Tag(name = "Relationships", description = "Relationship registration operations.")
public class RelationshipRegistrationController {

    private final RelationshipRegistrationService relationshipRegistrationService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public RelationshipRegistrationController(
            RelationshipRegistrationService relationshipRegistrationService,
            ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
            SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.relationshipRegistrationService = relationshipRegistrationService;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @GetMapping
    @Operation(summary = "List relationships", description = "Returns all registered semantic relationships.")
    public List<Map<String, Object>> listRelationships(
            @Parameter(description = "Lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCd) {
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lifecycle_status_cd", lifecycleStatusCd);
        return jdbcTemplate.queryForList(
                sqlQueryLoaderUtil.getQuery("semantic_relationship.find_all"),
                params
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register relationship", description = "Registers a semantic relationship between two objects.")
    public RelationshipRegistrationResponseDto registerRelationship(
            @Valid @RequestBody RelationshipRegistrationRequestDto request) {
        return relationshipRegistrationService.registerRelationship(request);
    }
}
