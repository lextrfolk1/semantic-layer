package com.lextr.semanticlayer.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opa")
public class OpaProperties {

    /**
     * Keep OPA opt-in so existing tests and local runs still work when the agent is absent.
     */
    private boolean enabled = false;

    /**
     * Root OPA server URL, for example http://opa:8181.
     */
    private String baseUrl = "http://localhost:8181";

    /**
     * OPA data API prefix.
     */
    private String decisionPathPrefix = "/v1/data";

    private Duration requestTimeout = Duration.ofSeconds(2);

    private String policyPath = "classpath:opa";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDecisionPathPrefix() {
        return decisionPathPrefix;
    }

    public void setDecisionPathPrefix(String decisionPathPrefix) {
        this.decisionPathPrefix = decisionPathPrefix;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getPolicyPath() {
        return policyPath;
    }

    public void setPolicyPath(String policyPath) {
        this.policyPath = policyPath;
    }

    private String policyIdPrefix = "policies/";

    public String getPolicyIdPrefix() {
        return policyIdPrefix;
    }

    public void setPolicyIdPrefix(String policyIdPrefix) {
        this.policyIdPrefix = policyIdPrefix;
    }
}
