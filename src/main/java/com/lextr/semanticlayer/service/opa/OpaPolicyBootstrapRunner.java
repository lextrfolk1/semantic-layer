package com.lextr.semanticlayer.service.opa;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

public class OpaPolicyBootstrapRunner implements ApplicationRunner {

    private final OpaPolicyReloadService reloadService;

    public OpaPolicyBootstrapRunner(OpaPolicyReloadService reloadService) {
        this.reloadService = reloadService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> reloadedPolicies = reloadService.reloadPolicies();
        if (reloadedPolicies.isEmpty()) {
            throw new IllegalStateException("No OPA policies were reloaded at startup");
        }
    }
}
