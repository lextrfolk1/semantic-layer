package com.lextr.semanticlayer.service.opa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

public class OpaPolicyBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(OpaPolicyBootstrapRunner.class);

    private final OpaPolicyReloadService reloadService;

    public OpaPolicyBootstrapRunner(OpaPolicyReloadService reloadService) {
        this.reloadService = reloadService;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Reloading OPA policies at startup");
        List<String> reloadedPolicies = reloadService.reloadPolicies();
        if (reloadedPolicies.isEmpty()) {
            logger.error("OPA startup reload returned no policies");
            throw new IllegalStateException("No OPA policies were reloaded at startup");
        }
        logger.info("OPA startup reload completed. resultCount={}", reloadedPolicies.size());
    }
}
