package com.lextr.semanticlayer.service.opa;

import com.lextr.semanticlayer.config.OpaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpaPolicyReloadService {
    private static final Logger log = LoggerFactory.getLogger(OpaPolicyReloadService.class);

    private final OpaProperties properties;
    private final ObjectProvider<OpaDecisionGateway> gatewayProvider;

    public OpaPolicyReloadService(OpaProperties properties, ObjectProvider<OpaDecisionGateway> gatewayProvider) {
        this.properties = properties;
        this.gatewayProvider = gatewayProvider;
    }

    public List<String> reloadPolicies() {
        List<String> reloaded = new ArrayList<>();
        OpaDecisionGateway gateway = gatewayProvider.getIfAvailable();

        if (gateway == null || !properties.isEnabled()) {
            log.info("OPA is disabled, skipping policy reload.");
            return reloaded;
        }

        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = properties.getPolicyPath();
            if (!pattern.endsWith("/")) {
                pattern += "/";
            }
            pattern += "**/*.rego";

            Resource[] resources = resolver.getResources(pattern);
            log.info("Found {} rego policies to reload from pattern: {}", resources.length, pattern);

            for (Resource resource : resources) {
                String policyId = getPolicyId(resource);
                String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                gateway.publishPolicy(policyId, content);
                reloaded.add(policyId);
                log.info("Successfully reloaded OPA policy: {}", policyId);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan or load OPA policy files", e);
        }
        return reloaded;
    }

    private String getPolicyId(Resource resource) throws IOException {
        String uri = resource.getURI().toString();
        int index = uri.indexOf("opa/");
        String relativePath;
        if (index != -1) {
            relativePath = uri.substring(index + 4);
        } else {
            relativePath = resource.getFilename();
        }

        String prefix = properties.getPolicyIdPrefix();
        if (prefix == null) {
            prefix = "";
        }
        return prefix + relativePath;
    }
}
