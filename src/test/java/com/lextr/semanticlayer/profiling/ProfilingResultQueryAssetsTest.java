package com.lextr.semanticlayer.profiling;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilingResultQueryAssetsTest {

    @Test
    void loadsProfilingResultQueryFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String query = loader.getQuery("profiling_result.find_all");

        assertTrue(query.contains("FROM meta.profiling_result"));
        assertTrue(query.contains("SELECT id, client_id, schema_cd, object_cd, logical_attribute_cd"));
        assertTrue(query.contains("attribute_role_cd"));
        assertTrue(query.contains("null_pct_nbr"));
        assertTrue(query.contains("distinct_pct_nbr"));
        assertTrue(query.contains("profiling_status_cd"));
        assertTrue(query.contains("last_profiled_ts"));
        assertTrue(query.contains("client_id = :client_id"));
        assertTrue(query.contains("schema_cd = :schema_cd"));
        assertTrue(query.contains("object_cd = :object_cd"));
        assertTrue(query.contains(":profiling_status_cd"));
        assertTrue(query.contains("ORDER BY logical_attribute_cd"));
    }

    @Test
    void returnsExpectedSnakeCaseColumnsForTheScreen() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String query = loader.getQuery("profiling_result.find_all");

        assertTrue(query.contains("id, client_id, schema_cd, object_cd, logical_attribute_cd, attribute_role_cd, null_pct_nbr, distinct_pct_nbr, profiling_status_cd, last_profiled_ts, created_ts, created_by, updated_ts, updated_by"));
    }
}
