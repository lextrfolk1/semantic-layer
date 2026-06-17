package com.lextr.semanticlayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticLayerApplicationTests {

    @Test
    void applicationClassLoads() {
        assertEquals("com.lextr.semanticlayer", SemanticLayerApplication.class.getPackageName());
    }

}
