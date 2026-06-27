package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.AttributePairingRegistrationResponseDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupCertificationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.dto.LogicalHierarchyDto;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.dto.SchemaCatalogDto;
import com.lextr.semanticlayer.dto.DataConnectionDto;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.service.AttributePairingRegistrationService;
import com.lextr.semanticlayer.service.AttributePairingResolutionService;
import com.lextr.semanticlayer.service.FilterLookupBindingService;
import com.lextr.semanticlayer.service.FilterLookupCertificationService;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
import com.lextr.semanticlayer.service.FilterLookupReadService;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import com.lextr.semanticlayer.service.ConsumptionService;
import com.lextr.semanticlayer.service.GovernanceHistoryReadService;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
import com.lextr.semanticlayer.service.DqRuleService;
import com.lextr.semanticlayer.service.ProfilingResultReadService;
import com.lextr.semanticlayer.service.LogicalPhysicalResolutionService;
import com.lextr.semanticlayer.service.HierarchyService;
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import com.lextr.semanticlayer.service.RuleResultService;
import com.lextr.semanticlayer.service.RegistryReadService;
import com.lextr.semanticlayer.service.RelationshipRegistrationService;
import com.lextr.semanticlayer.service.WorkspaceService;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.event.ApplicationEventsTestExecutionListener;
import org.springframework.test.context.event.EventPublishingTestExecutionListener;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.web.ServletTestExecutionListener;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.extension.ExtendWith;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.time.LocalDate;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OpenApiDocumentationTest.OpenApiTestApplication.class)
@WebAppConfiguration
@TestExecutionListeners(
        value = {
                ServletTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                ApplicationEventsTestExecutionListener.class,
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class,
                EventPublishingTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class OpenApiDocumentationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void loadsOpenApiSpecForCurrentControllerSurface() throws Exception {
        String response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        assertTrue(root.path("info").path("title").asText().contains("Semantic Layer API"));

        JsonNode paths = root.path("paths");
        assertControllerRoutesAreDocumented(paths);

        JsonNode schemaGet = paths.path("/api/registry/schemas").path("get");
        assertQueryParameter(schemaGet, "client_id");
        assertQueryParameter(schemaGet, "lifecycle_status_cd");

        JsonNode relationshipPost = paths.path("/api/relationships").path("post");
        assertJsonRequestBody(relationshipPost);

        JsonNode previewPost = paths.path("/api/filter-lookups/preview").path("post");
        assertJsonRequestBody(previewPost);

        JsonNode governanceHistoryGet = paths.path("/api/governance/history").path("get");
        assertQueryParameter(governanceHistoryGet, "client_id");
        assertQueryParameter(governanceHistoryGet, "entity_type_cd");
        assertQueryParameter(governanceHistoryGet, "entity_ref");

        JsonNode workflowApprovePost = paths.path("/api/workflow-tasks/{id}/approve").path("post");
        assertJsonRequestBody(workflowApprovePost);

        JsonNode observabilityIngestPost = paths.path("/api/observability-signals").path("post");
        assertJsonRequestBody(observabilityIngestPost);

        JsonNode observabilityCorrelatePost = paths.path("/api/observability-signals/{signal_id}/correlate").path("post");
        assertJsonRequestBody(observabilityCorrelatePost);

        JsonNode workspacesGet = paths.path("/api/workspaces").path("get");
        assertQueryParameter(workspacesGet, "tenant_cd");

        JsonNode workspacesPost = paths.path("/api/workspaces").path("post");
        assertJsonRequestBody(workspacesPost);

        JsonNode profilingMetricsGet = paths.path("/api/profiling/metrics").path("get");
        assertQueryParameter(profilingMetricsGet, "client_id");
        assertQueryParameter(profilingMetricsGet, "object_id");
        assertQueryParameter(profilingMetricsGet, "profiling_status_cd");

        JsonNode hierarchiesGet = paths.path("/api/hierarchies").path("get");
        assertQueryParameter(hierarchiesGet, "tenant_cd");

        JsonNode hierarchiesPost = paths.path("/api/hierarchies").path("post");
        assertJsonRequestBody(hierarchiesPost);

        JsonNode ruleResultsPost = paths.path("/api/rule-results").path("post");
        assertJsonRequestBody(ruleResultsPost);

        JsonNode workflowTasksGet = paths.path("/api/workflow-tasks").path("get");
        assertQueryParameter(workflowTasksGet, "client_id");

        JsonNode workflowTasksRejectPost = paths.path("/api/workflow-tasks/{id}/reject").path("post");
        assertJsonRequestBody(workflowTasksRejectPost);

        JsonNode dqRulesGet = paths.path("/api/dq-rules").path("get");
        assertQueryParameter(dqRulesGet, "client_id");
        assertQueryParameter(dqRulesGet, "rule_dimension_cd");
        assertQueryParameter(dqRulesGet, "lifecycle_status_cd");

        JsonNode dqRulesPost = paths.path("/api/dq-rules/requests").path("post");
        assertJsonRequestBody(dqRulesPost);

        JsonNode consumptionLayersGet = paths.path("/api/consumption/layers").path("get");
        assertQueryParameter(consumptionLayersGet, "client_id");
        assertQueryParameter(consumptionLayersGet, "lifecycle_status_cd");

        JsonNode consumptionExposuresGet = paths.path("/api/consumption/exposures").path("get");
        assertQueryParameter(consumptionExposuresGet, "client_id");
        assertQueryParameter(consumptionExposuresGet, "object_id");
        assertQueryParameter(consumptionExposuresGet, "structure_type_cd");

        JsonNode consumptionPromotePost = paths.path("/api/consumption/exposures/{exposure_id}/promote").path("post");
        assertQueryParameter(consumptionPromotePost, "client_id");
        assertJsonRequestBody(consumptionPromotePost);

        JsonNode logicalPhysicalAttributesGet = paths.path("/api/logical-physical-resolutions/attributes").path("get");
        assertQueryParameter(logicalPhysicalAttributesGet, "client_id");
        assertQueryParameter(logicalPhysicalAttributesGet, "schema_cd");
        assertQueryParameter(logicalPhysicalAttributesGet, "object_cd");
        assertQueryParameter(logicalPhysicalAttributesGet, "logical_attribute_cd");

        JsonNode logicalPhysicalOutboundGet = paths.path("/api/logical-physical-resolutions/outbounds/{outbound_id}").path("get");
        assertQueryParameter(logicalPhysicalOutboundGet, "client_id");

        JsonNode semanticResolvePost = paths.path("/api/semantic/resolve").path("post");
        assertJsonRequestBody(semanticResolvePost);

        JsonNode consumptionResolveGet = paths.path("/api/consumption/{outbound_id}/resolve").path("get");
        assertQueryParameter(consumptionResolveGet, "client_id");

        assertFalse(hasMultipartRequestBody(paths));
    }

    private static void assertQueryParameter(JsonNode operation, String parameterName) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameterName.equals(parameter.path("name").asText())
                    && "query".equals(parameter.path("in").asText())) {
                return;
            }
        }
        throw new AssertionError("Missing query parameter " + parameterName);
    }

    private void assertControllerRoutesAreDocumented(JsonNode paths) {
        requestMappingHandlerMapping.getHandlerMethods().forEach((mappingInfo, handlerMethod) -> {
            Class<?> beanType = handlerMethod.getBeanType();
            if (!beanType.getPackageName().startsWith("com.lextr.semanticlayer.api")) {
                return;
            }
            if (!beanType.isAnnotationPresent(RestController.class)) {
                return;
            }

            assertTrue(beanType.isAnnotationPresent(Tag.class), "Missing @Tag on " + beanType.getSimpleName());
            assertTrue(handlerMethod.getMethod().isAnnotationPresent(Operation.class),
                    "Missing @Operation on " + beanType.getSimpleName() + "#" + handlerMethod.getMethod().getName());

            for (String pattern : extractPatterns(mappingInfo)) {
                assertTrue(paths.has(pattern), "Missing OpenAPI path " + pattern);
            }
        });
    }

    private static Set<String> extractPatterns(RequestMappingInfo mappingInfo) {
        if (mappingInfo.getPathPatternsCondition() != null) {
            return mappingInfo.getPathPatternsCondition().getPatternValues();
        }
        if (mappingInfo.getPatternsCondition() != null) {
            return mappingInfo.getPatternsCondition().getPatterns();
        }
        return Set.of();
    }

    private static void assertJsonRequestBody(JsonNode operation) {
        JsonNode jsonBody = operation.path("requestBody").path("content").path("application/json");
        assertNotNull(jsonBody);
        assertFalse(jsonBody.isMissingNode());
    }

    private static boolean hasMultipartRequestBody(JsonNode paths) {
        Iterator<JsonNode> pathIterator = paths.elements();
        while (pathIterator.hasNext()) {
            JsonNode pathItem = pathIterator.next();
            Iterator<String> methods = pathItem.fieldNames();
            while (methods.hasNext()) {
                JsonNode operation = pathItem.path(methods.next());
                if (operation.path("requestBody").path("content").has("multipart/form-data")) {
                    return true;
                }
            }
        }
        return false;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            JdbcRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            Neo4jAutoConfiguration.class,
            Neo4jDataAutoConfiguration.class
    })
    @Import({
            OpenApiConfiguration.class,
            ApiExceptionHandler.class,
            OpenApiTestApplication.ServiceStubs.class,
            RegistryReadController.class,
            ObjectRegistrationController.class,
            RelationshipRegistrationController.class,
            FilterLookupRegistrationController.class,
            GovernanceHistoryController.class,
            GovernancePolicyPresetController.class,
            ObservabilitySignalController.class,
            WorkflowTaskController.class,
            RuleResultController.class,
            HierarchyController.class,
            WorkspaceController.class,
            ProfilingResultController.class,
            AttributePairingController.class,
            DqRuleController.class,
            ConsumptionController.class,
            LogicalPhysicalResolutionController.class,
            SemanticResolveController.class,
            ConsumptionResolveController.class,
            SQLQueryLoaderUtil.class
    })
    static class OpenApiTestApplication {

        @Configuration
        static class ServiceStubs {

            @Bean
            RegistryReadService registryReadService() {
                return new RegistryReadService() {
                    @Override
                    public List<SchemaCatalogDto> findSchemas(String clientId, String lifecycleStatusCode) {
                        return List.of();
                    }

                    @Override
                    public SchemaCatalogDto findSchema(String clientId, String schemaCode) {
                        return null;
                    }

                    @Override
                    public List<DataConnectionDto> findConnections(String clientId, String engineCode, Boolean activeFlag) {
                        return List.of();
                    }

                    @Override
                    public DataConnectionDto findConnection(String clientId, UUID connectionId) {
                        return null;
                    }
                };
            }

            @Bean
            ObjectRegistrationService objectRegistrationService() {
                return request -> null;
            }

            @Bean
            ObjectExposureReadService objectExposureReadService() {
                return new ObjectExposureReadService() {
                    @Override
                    public List<ObjectExposureSummaryDto> findObjects(String clientId,
                                                                      String actorId,
                                                                      String roleCode,
                                                                      String purposeCode,
                                                                      String schemaCode,
                                                                      String lifecycleStatusCode) {
                        return List.of();
                    }

                    @Override
                    public ObjectExposureDetailDto findObject(String clientId,
                                                              String actorId,
                                                              String roleCode,
                                                              String purposeCode,
                                                              UUID objectId) {
                        return null;
                    }
                };
            }

            @Bean
            RelationshipRegistrationService relationshipRegistrationService() {
                return request -> null;
            }

            @Bean
            FilterLookupRegistrationService filterLookupRegistrationService() {
                return request -> null;
            }

            @Bean
            FilterLookupReadService filterLookupReadService() {
                return new FilterLookupReadService() {
                    @Override
                    public List<FilterLookupEffectiveReviewDto> findLookups(String clientId, String governanceStatusCode, String healthStatusCode, String lifecycleStatusCode) {
                        return List.of();
                    }

                    @Override
                    public FilterLookupEffectiveReviewDto findLookup(String clientId, String lookupCode) {
                        return null;
                    }
                };
            }

            @Bean
            FilterLookupBindingService filterLookupBindingService() {
                return (lookupCode, request) -> null;
            }

            @Bean
            FilterLookupCertificationService filterLookupCertificationService() {
                return (lookupCode, request) -> null;
            }

            @Bean
            FilterLookupPreviewService filterLookupPreviewService() {
                return request -> List.of();
            }

            @Bean
            GovernanceHistoryReadService governanceHistoryReadService() {
                return (clientId, entityTypeCode, entityRef, changeTypeCode) -> List.of();
            }

            @Bean
            GovernancePolicyPresetReadService governancePolicyPresetReadService() {
                return new GovernancePolicyPresetReadService() {
                    @Override
                    public List<GovernancePolicyPresetDto> findPolicyPresets(String clientId, String policyScopeCode, LocalDate asOfDate) {
                        return List.of();
                    }

                    @Override
                    public GovernancePolicyPresetDto findPolicyPreset(String clientId, String policyCode, String policyScopeCode, LocalDate asOfDate) {
                        return null;
                    }
                };
            }

            @Bean
            WorkflowApprovalService workflowApprovalService() {
                return new WorkflowApprovalService() {
                    @Override
                    public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
                        return null;
                    }
                    @Override
                    public WorkflowTaskResponseDto rejectTask(Long id, java.util.Map<String, String> body) {
                        return null;
                    }
                };
            }

            @Bean
            HierarchyService hierarchyService() {
                return new HierarchyService() {
                    @Override
                    public List<LogicalHierarchyDto> findAll(String tenantCd) {
                        return List.of();
                    }

                    @Override
                    public LogicalHierarchyDto createHierarchy(String hierarchyCd, String hierarchyNm, String tenantCd, String hierarchyStatusCd, String createdBy) {
                        return null;
                    }
                };
            }

            @Bean
            RuleResultService ruleResultService() {
                return (request, principalCd) -> null;
            }

            @Bean
            WorkspaceService workspaceService() {
                return new WorkspaceService() {
                    @Override
                    public List<com.lextr.semanticlayer.dto.TenantWorkspaceDto> findAll(String tenantCd) {
                        return List.of();
                    }

                    @Override
                    public com.lextr.semanticlayer.dto.TenantWorkspaceDto createWorkspace(String workspaceCd, String tenantCd, String workspaceNm, String workspaceDesc, String workspaceStatusCd, String createdBy) {
                        return null;
                    }
                };
            }

            @Bean
            ProfilingResultReadService profilingResultReadService() {
                return (clientId, objectId, profilingStatusCode) -> List.of();
            }

            @Bean
            DqRuleService dqRuleService() {
                return new DqRuleService() {
                    @Override
                    public List<com.lextr.semanticlayer.dto.DqRuleCatalogDto> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
                        return List.of();
                    }

                    @Override
                    public com.lextr.semanticlayer.dto.DqRuleCatalogDto findRule(String clientId, String ruleCode) {
                        return null;
                    }

                    @Override
                    public List<com.lextr.semanticlayer.dto.DqRuleAttributeDto> findRuleAttributes(String clientId, String ruleCode) {
                        return List.of();
                    }

                    @Override
                    public List<com.lextr.semanticlayer.dto.DqRuleResultDto> findRuleResults(String clientId, String logicalAttributeCode) {
                        return List.of();
                    }

                    @Override
                    public com.lextr.semanticlayer.dto.DqRuleResultDto ingestResult(com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto request, String principalCd) {
                        return null;
                    }

                    @Override
                    public List<com.lextr.semanticlayer.dto.WorkflowTaskResponseDto> requestRules(com.lextr.semanticlayer.dto.DqRuleRequestDto request) {
                        return List.of();
                    }

                    @Override
                    public com.lextr.semanticlayer.dto.WorkflowTaskResponseDto findRequest(String clientId, UUID workflowTaskId) {
                        return null;
                    }
                };
            }

            @Bean
            ConsumptionService consumptionService() {
                return new ConsumptionService() {
                    @Override
                    public List<com.lextr.semanticlayer.dto.ConsumptionLayerDto> findLayers(String clientId, String lifecycleStatusCode) {
                        return List.of();
                    }

                    @Override
                    public com.lextr.semanticlayer.dto.ConsumptionLayerDto findLayer(String clientId, String layerCode) {
                        return null;
                    }

                    @Override
                    public List<com.lextr.semanticlayer.dto.ConsumptionExposureDto> findExposures(String clientId, UUID objectId, String structureTypeCode) {
                        return List.of();
                    }

                    @Override
                    public com.lextr.semanticlayer.dto.ConsumptionLayerDto registerConsumptionLayer(com.lextr.semanticlayer.dto.ConsumptionLayerRegistrationRequestDto request) {
                        return null;
                    }

                    @Override
                    public com.lextr.semanticlayer.dto.ConsumptionExposureDto promoteExposure(String clientId, Long exposureId, com.lextr.semanticlayer.dto.ConsumptionPromotionRequestDto request) {
                        return null;
                    }
                };
            }

            @Bean
            AttributePairingRegistrationService attributePairingRegistrationService() {
                return request -> null;
            }

            @Bean
            AttributePairingResolutionService attributePairingResolutionService() {
                return request -> null;
            }

            @Bean
            LogicalPhysicalResolutionService logicalPhysicalResolutionService() {
                return new LogicalPhysicalResolutionService() {
                    @Override
                    public List<LogicalPhysicalResolutionDto> resolveAttributes(String clientId, String schemaCode, String objectCode, List<String> logicalAttributeCodes) {
                        return List.of();
                    }

                    @Override
                    public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId, Long outboundId) {
                        return List.of();
                    }
                };
            }
        }
    }
}
