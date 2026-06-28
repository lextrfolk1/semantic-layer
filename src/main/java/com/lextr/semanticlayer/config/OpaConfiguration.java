package com.lextr.semanticlayer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.AttributePairingPolicyDecisionDto;
import com.lextr.semanticlayer.dto.AttributePairingPolicyRequestDto;
import com.lextr.semanticlayer.dto.ConsumptionPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ConsumptionPolicyRequestDto;
import com.lextr.semanticlayer.dto.DqRulePolicyDecisionDto;
import com.lextr.semanticlayer.dto.DqRuleRequestDto;
import com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.ExternalRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupCertificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposureAccessPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalAutoTriggerPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalPolicyDecisionDto;
import com.lextr.semanticlayer.dto.RelationshipPolicyDecisionDto;
import com.lextr.semanticlayer.dto.RelationshipPolicyRequestDto;
import com.lextr.semanticlayer.dto.RuleResultPolicyDecisionDto;
import com.lextr.semanticlayer.dto.SemanticResolvePolicyRequestDto;
import com.lextr.semanticlayer.dto.TaxonomyPolicyDecisionDto;
import com.lextr.semanticlayer.dto.TaxonomyPolicyRequestDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyDecisionDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyRequestDto;
import com.lextr.semanticlayer.service.AttributePairingPolicyClient;
import com.lextr.semanticlayer.service.ConsumptionPolicyClient;
import com.lextr.semanticlayer.service.DqRulePolicyClient;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
import com.lextr.semanticlayer.service.ObjectExposurePolicyClient;
import com.lextr.semanticlayer.service.ObservabilitySignalPolicyClient;
import com.lextr.semanticlayer.service.RelationshipPolicyClient;
import com.lextr.semanticlayer.service.RuleResultPolicyClient;
import com.lextr.semanticlayer.service.SemanticResolvePolicyClient;
import com.lextr.semanticlayer.service.TaxonomyPolicyClient;
import com.lextr.semanticlayer.service.WorkflowPolicyClient;
import com.lextr.semanticlayer.service.opa.OpaPolicyBootstrapRunner;
import com.lextr.semanticlayer.service.opa.OpaDecisionGateway;
import com.lextr.semanticlayer.service.opa.OpaPolicyReloadService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpaProperties.class)
@ConditionalOnProperty(prefix = "opa", name = "enabled", havingValue = "true")
public class OpaConfiguration {

    private static final String OBJECT_EXPOSURE_ACCESS_PACKAGE = "lextr.semantic.object_exposure";
    private static final String OBJECT_EXPOSURE_CLASSIFICATION_PACKAGE = "lextr.semantic.object_exposure.classification";
    private static final String FILTER_LOOKUP_PACKAGE = "lextr.semantic.filter_lookup";
    private static final String FILTER_LOOKUP_CERTIFICATION_PACKAGE = "lextr.semantic.filter_lookup_certification";
    private static final String FILTER_LOOKUP_BINDING_PACKAGE = "lextr.semantic.filter_lookup_binding";
    private static final String CONSUMPTION_PACKAGE = "lextr.semantic.consumption_layer";
    private static final String RELATIONSHIP_PACKAGE = "lextr.semantic.relationship";
    private static final String ATTRIBUTE_PAIRING_PACKAGE = "lextr.semantic.attribute_pairing";
    private static final String TAXONOMY_PACKAGE = "lextr.semantic.taxonomy";
    private static final String DQ_REQUEST_OBSERVE_PACKAGE = "lextr.semantic.dq_request_observe";
    private static final String OBSERVABILITY_SIGNAL_PACKAGE = "lextr.semantic.observability_signal";
    private static final String SEMANTIC_RESOLVE_PACKAGE = "lextr.semantic.semantic_resolve";
    private static final String RULE_RESULTS_PACKAGE = "lextr.semantic.rule_results";
    private static final String WORKFLOW_APPROVAL_PACKAGE = "lextr.semantic.workflow_approval";

    @Bean
    @ConditionalOnMissingBean
    OpaDecisionGateway opaDecisionGateway(OpaProperties properties, ObjectMapper objectMapper) {
        return new OpaDecisionGateway(properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ApplicationRunner opaPolicyBootstrapRunner(org.springframework.beans.factory.ObjectProvider<OpaPolicyReloadService> reloadServiceProvider) {
        return args -> {
            OpaPolicyReloadService reloadService = reloadServiceProvider.getIfAvailable();
            if (reloadService == null) {
                return;
            }
            new OpaPolicyBootstrapRunner(reloadService).run(args);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    AttributePairingPolicyClient attributePairingPolicyClient(OpaDecisionGateway gateway) {
        return request -> gateway.evaluate(ATTRIBUTE_PAIRING_PACKAGE, request, AttributePairingPolicyDecisionDto.class);
    }

    @Bean
    @ConditionalOnMissingBean
    RelationshipPolicyClient relationshipPolicyClient(OpaDecisionGateway gateway) {
        return request -> gateway.evaluate(RELATIONSHIP_PACKAGE, request, RelationshipPolicyDecisionDto.class);
    }

    @Bean
    @ConditionalOnMissingBean
    TaxonomyPolicyClient taxonomyPolicyClient(OpaDecisionGateway gateway) {
        return request -> gateway.evaluate(TAXONOMY_PACKAGE, request, TaxonomyPolicyDecisionDto.class);
    }

    @Bean
    @ConditionalOnMissingBean
    ObservabilitySignalPolicyClient observabilitySignalPolicyClient(OpaDecisionGateway gateway) {
        return request -> gateway.evaluate(OBSERVABILITY_SIGNAL_PACKAGE, request, ObservabilitySignalPolicyDecisionDto.class);
    }

    @Bean
    @ConditionalOnMissingBean
    SemanticResolvePolicyClient semanticResolvePolicyClient(OpaDecisionGateway gateway) {
        return request -> gateway.evaluate(SEMANTIC_RESOLVE_PACKAGE, request, ObjectExposurePolicyDecisionDto.class);
    }

    @Bean
    @ConditionalOnMissingBean
    ObjectExposurePolicyClient objectExposurePolicyClient(OpaDecisionGateway gateway) {
        return new ObjectExposurePolicyClient() {
            @Override
            public ObjectExposurePolicyDecisionDto evaluateAccess(ObjectExposureAccessPolicyRequestDto request) {
                return gateway.evaluate(OBJECT_EXPOSURE_ACCESS_PACKAGE, request, ObjectExposurePolicyDecisionDto.class);
            }

            @Override
            public ObjectExposureClassificationPolicyDecisionDto evaluateClassification(
                    ObjectExposureClassificationPolicyRequestDto request
            ) {
                return gateway.evaluate(OBJECT_EXPOSURE_CLASSIFICATION_PACKAGE, request, ObjectExposureClassificationPolicyDecisionDto.class);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    FilterLookupPolicyClient filterLookupPolicyClient(OpaDecisionGateway gateway) {
        return new FilterLookupPolicyClient() {
            @Override
            public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(FilterLookupPolicyRequestDto request) {
                return gateway.evaluate(FILTER_LOOKUP_PACKAGE, request, FilterLookupPolicyDecisionDto.class);
            }

            @Override
            public FilterLookupPolicyDecisionDto validateCertification(FilterLookupCertificationPolicyRequestDto request) {
                return gateway.evaluate(FILTER_LOOKUP_CERTIFICATION_PACKAGE, request, FilterLookupPolicyDecisionDto.class);
            }

            @Override
            public FilterLookupPolicyDecisionDto validateBinding(FilterLookupBindingPolicyRequestDto request) {
                return gateway.evaluate(FILTER_LOOKUP_BINDING_PACKAGE, request, FilterLookupPolicyDecisionDto.class);
            }

            @Override
            public FilterLookupPolicyDecisionDto validatePreviewExecution(FilterLookupPreviewPolicyRequestDto request) {
                return new FilterLookupPolicyDecisionDto(true, null, null);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    DqRulePolicyClient dqRulePolicyClient(OpaDecisionGateway gateway) {
        return new DqRulePolicyClient() {
            @Override
            public DqRulePolicyDecisionDto validateRequest(DqRuleRequestDto request) {
                return gateway.evaluate(DQ_REQUEST_OBSERVE_PACKAGE, dqRequestInput(request), DqRulePolicyDecisionDto.class);
            }

            @Override
            public DqRulePolicyDecisionDto validateResultIngest(DqRuleResultIngestRequestDto request, String principalCd) {
                return gateway.evaluate(DQ_REQUEST_OBSERVE_PACKAGE, dqResultInput(request, principalCd), DqRulePolicyDecisionDto.class);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    ConsumptionPolicyClient consumptionPolicyClient(OpaDecisionGateway gateway) {
        return new ConsumptionPolicyClient() {
            @Override
            public ConsumptionPolicyDecisionDto validatePromotion(ConsumptionPolicyRequestDto request) {
                return gateway.evaluate(CONSUMPTION_PACKAGE, consumptionInput(request), ConsumptionPolicyDecisionDto.class);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    RuleResultPolicyClient ruleResultPolicyClient(OpaDecisionGateway gateway) {
        return new RuleResultPolicyClient() {
            @Override
            public RuleResultPolicyDecisionDto validateIngest(ExternalRuleResultIngestRequestDto request, String principalCd) {
                return gateway.evaluate(RULE_RESULTS_PACKAGE, ruleResultInput(request, principalCd), RuleResultPolicyDecisionDto.class);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    WorkflowPolicyClient workflowPolicyClient(OpaDecisionGateway gateway) {
        return request -> gateway.evaluate(WORKFLOW_APPROVAL_PACKAGE, request, WorkflowPolicyDecisionDto.class);
    }

    private Map<String, Object> dqRequestInput(DqRuleRequestDto request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("request_type_cd", "REQUEST");
        input.put("client_id", request.client_id());
        input.put("rule_names", request.rule_names());
        input.put("requested_by", request.requested_by());
        input.put("request_txt", request.request_txt());
        return input;
    }

    private Map<String, Object> dqResultInput(DqRuleResultIngestRequestDto request, String principalCd) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("policy_cd", "POL-DQ-001");
        input.put("request_type_cd", "RESULT");
        input.put("client_id", request.client_id());
        input.put("rule_cd", request.rule_cd());
        input.put("logical_attribute_cd", request.logical_attribute_cd());
        input.put("observed_value_txt", request.observed_value_txt());
        input.put("expected_value_txt", request.expected_value_txt());
        input.put("result_status_cd", request.result_status_cd());
        input.put("result_reason_txt", request.result_reason_txt());
        input.put("observed_ts", request.observed_ts());
        input.put("principal_cd", principalCd);
        input.put("ingested_by", request.ingested_by());
        return input;
    }

    private Map<String, Object> consumptionInput(ConsumptionPolicyRequestDto request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("policy_cd", "POL-CL-001");
        input.put("client_id", request.client_id());
        input.put("source_sdlc_status_cd", request.source_sdlc_status_cd());
        input.put("target_sdlc_status_cd", request.target_sdlc_status_cd());
        input.put("single_engine_flg", true);
        input.put("governance_status_cd", "APPROVED");
        input.put("approved_by", request.promoted_by());
        input.put("approved_ts", OffsetDateTime.now(ZoneOffset.UTC));
        input.put("exposure_id", request.exposure_id());
        input.put("promoted_by", request.promoted_by());
        input.put("promotion_reason_txt", request.promotion_reason_txt());
        return input;
    }

    private Map<String, Object> ruleResultInput(ExternalRuleResultIngestRequestDto request, String principalCd) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("policy_cd", "POL-RR-001");
        input.put("request_type_cd", "RULE_RESULT");
        input.put("client_id", request.client_id());
        input.put("outbound_id", request.outbound_id());
        input.put("rule_ref_cd", request.rule_ref_cd());
        input.put("output_kind_cd", request.output_kind_cd());
        input.put("principal_cd", principalCd);
        return input;
    }
}
