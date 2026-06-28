package com.lextr.semanticlayer.service.opa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpaPolicyBootstrapRunnerTest {

    @Test
    void reloadsPoliciesOnStartup() throws Exception {
        RecordingOpaPolicyReloadService reloadService = new RecordingOpaPolicyReloadService();
        OpaPolicyBootstrapRunner runner = new OpaPolicyBootstrapRunner(reloadService);

        runner.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(1, reloadService.reloadCount);
    }

    @Test
    void failsFastWhenNoPoliciesAreReloaded() {
        OpaPolicyBootstrapRunner runner = new OpaPolicyBootstrapRunner(new RecordingOpaPolicyReloadService(true));

        assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments(new String[0])));
    }

    private static final class RecordingOpaPolicyReloadService extends OpaPolicyReloadService {
        private int reloadCount;
        private final boolean returnEmpty;

        private RecordingOpaPolicyReloadService() {
            this(false);
        }

        private RecordingOpaPolicyReloadService(boolean returnEmpty) {
            super(null, null);
            this.returnEmpty = returnEmpty;
        }

        @Override
        public java.util.List<String> reloadPolicies() {
            reloadCount++;
            if (returnEmpty) {
                return java.util.List.of();
            }
            return java.util.List.of("opa/relationship/cross_engine_block.rego");
        }
    }
}
