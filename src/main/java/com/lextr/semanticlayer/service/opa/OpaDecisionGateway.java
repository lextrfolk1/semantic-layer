package com.lextr.semanticlayer.service.opa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.config.OpaProperties;
import com.lextr.semanticlayer.exception.OpaPolicyClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpaDecisionGateway {

    private static final Logger logger = LoggerFactory.getLogger(OpaDecisionGateway.class);

    private final OpaProperties properties;
    private final ObjectMapper objectMapper;
    private final OpaTransport transport;

    public OpaDecisionGateway(OpaProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new HttpClientOpaTransport(properties));
    }

    OpaDecisionGateway(OpaProperties properties, ObjectMapper objectMapper, OpaTransport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    public <T> T evaluate(String policyPackage, Object input, Class<T> decisionType) {
        try {
            logger.debug("Evaluating OPA policy. policyPackage={}, decisionType={}", policyPackage, decisionType.getSimpleName());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildUri(policyPackage))
                    .timeout(resolveTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(envelope(input))))
                    .build();

            HttpResponse<String> response = transport.send(request);
            if (response.statusCode() / 100 != 2) {
                logger.error("OPA policy evaluation failed with non-2xx status. policyPackage={}, statusCode={}", policyPackage, response.statusCode());
                throw new OpaPolicyClientException(buildFailureMessage(policyPackage, response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result");
            if (result.isMissingNode() || result.isNull()) {
                logger.error("OPA policy evaluation returned no result. policyPackage={}", policyPackage);
                throw new OpaPolicyClientException("OPA response for " + policyPackage + " did not contain result");
            }
            logger.debug("OPA policy evaluation completed. policyPackage={}, statusCode={}", policyPackage, response.statusCode());
            return objectMapper.convertValue(result, decisionType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("OPA policy evaluation interrupted. policyPackage={}, errorMessage={}", policyPackage, exception.getMessage(), exception);
            throw new OpaPolicyClientException("OPA evaluation interrupted for " + policyPackage, exception);
        } catch (IOException exception) {
            logger.error("OPA policy evaluation failed. policyPackage={}, errorMessage={}", policyPackage, exception.getMessage(), exception);
            throw new OpaPolicyClientException("OPA evaluation failed for " + policyPackage, exception);
        }
    }

    private URI buildUri(String policyPackage) {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        String decisionPrefix = normalizePathPrefix(properties.getDecisionPathPrefix());
        String policyPath = policyPackage.replace('.', '/');
        return URI.create(baseUrl + decisionPrefix + "/" + policyPath + "/evaluate");
    }

    private Duration resolveTimeout() {
        return properties.getRequestTimeout() == null ? Duration.ofSeconds(2) : properties.getRequestTimeout();
    }

    private Map<String, Object> envelope(Object input) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("input", input);
        return envelope;
    }

    private String buildFailureMessage(String policyPackage, int statusCode, String body) {
        String responseBody = body == null || body.isBlank() ? "<empty>" : body;
        return "OPA evaluation failed for " + policyPackage + " with HTTP " + statusCode + ": " + responseBody;
    }

    public void publishPolicy(String policyId, String regoContent) {
        try {
            logger.debug("Publishing OPA policy. policyId={}", policyId);
            String baseUrl = trimTrailingSlash(properties.getBaseUrl());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/policies/" + policyId))
                    .timeout(resolveTimeout())
                    .header("Content-Type", "text/plain")
                    .PUT(HttpRequest.BodyPublishers.ofString(regoContent))
                    .build();

            HttpResponse<String> response = transport.send(request);
            if (response.statusCode() / 100 != 2) {
                logger.error("OPA policy publish failed with non-2xx status. policyId={}, statusCode={}", policyId, response.statusCode());
                throw new OpaPolicyClientException("Failed to publish OPA policy " + policyId + ": HTTP " + response.statusCode() + " - " + response.body());
            }
            logger.debug("OPA policy published. policyId={}, statusCode={}", policyId, response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("OPA policy publish interrupted. policyId={}, errorMessage={}", policyId, exception.getMessage(), exception);
            throw new OpaPolicyClientException("OPA policy publish interrupted for " + policyId, exception);
        } catch (IOException exception) {
            logger.error("OPA policy publish failed. policyId={}, errorMessage={}", policyId, exception.getMessage(), exception);
            throw new OpaPolicyClientException("OPA policy publish failed for " + policyId, exception);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8181";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizePathPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "/v1/data";
        }
        String trimmed = value.startsWith("/") ? value : "/" + value;
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    interface OpaTransport {

        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private static final class HttpClientOpaTransport implements OpaTransport {

        private final HttpClient httpClient;

        private HttpClientOpaTransport(OpaProperties properties) {
            Duration connectTimeout = properties.getRequestTimeout() == null ? Duration.ofSeconds(2) : properties.getRequestTimeout();
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .build();
        }

        @Override
        public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
