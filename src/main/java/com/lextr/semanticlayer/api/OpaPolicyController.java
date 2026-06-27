package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.service.opa.OpaPolicyReloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/governance/policies")
public class OpaPolicyController {

    private final OpaPolicyReloadService reloadService;

    public OpaPolicyController(OpaPolicyReloadService reloadService) {
        this.reloadService = reloadService;
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadPolicies() {
        List<String> reloaded = reloadService.reloadPolicies();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "reloaded_policies", reloaded
        ));
    }
}
