package com.lextr.semanticlayer.service.opa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.config.OpaProperties;
import com.lextr.semanticlayer.exception.OpaPolicyClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpaDecisionGateway {

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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildUri(policyPackage))
                    .timeout(resolveTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(envelope(input))))
                    .build();

            HttpResponse<String> response = transport.send(request);
            if (response.statusCode() / 100 != 2) {
                throw new OpaPolicyClientException(buildFailureMessage(policyPackage, response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result");
            if (result.isMissingNode() || result.isNull()) {
                throw new OpaPolicyClientException("OPA response for " + policyPackage + " did not contain result");
            }
            return objectMapper.convertValue(result, decisionType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpaPolicyClientException("OPA evaluation interrupted for " + policyPackage, exception);
        } catch (IOException exception) {
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
