package com.lextr.semanticlayer.resolution;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicalPhysicalResolutionQueryAssetsTest {

    @Test
    void loadsLogicalPhysicalResolutionQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String attributeQuery = loader.getQuery("logical_physical_resolution.find_by_attributes");
        String outboundQuery = loader.getQuery("logical_physical_resolution.find_by_outbound_grain");

        assertTrue(attributeQuery.contains("FROM meta.object_catalog o"));
        assertTrue(attributeQuery.contains("JOIN meta.data_connection dc ON dc.connection_id = o.connection_id"));
        assertTrue(attributeQuery.contains("JOIN meta.attribute_catalog a ON a.schema_cd = o.schema_cd AND a.object_cd = o.object_cd"));
        assertTrue(attributeQuery.contains("meta.attribute_logical_name_override"));
        assertTrue(attributeQuery.contains("COALESCE(lno.override_nm, a.physical_attribute_nm) AS effective_logical_attribute_nm"));
        assertTrue(attributeQuery.contains("a.physical_attribute_nm"));
        assertTrue(attributeQuery.contains("o.source_object_nm"));
        assertTrue(attributeQuery.contains("dc.engine_cd"));
        assertTrue(attributeQuery.contains("a.canonical_data_type_cd AS data_type_cd"));
        assertTrue(attributeQuery.contains("NULL::bigint AS outbound_id"));
        assertTrue(attributeQuery.contains("IN (:logical_attribute_cds)"));
        assertTrue(attributeQuery.contains("ORDER BY CASE WHEN o.client_id = :client_id THEN 0 ELSE 1 END, a.attribute_cd"));
        assertFalse(attributeQuery.contains("INSERT INTO"));
        assertFalse(attributeQuery.contains("UPDATE "));
        assertFalse(attributeQuery.contains("DELETE FROM"));

        assertTrue(outboundQuery.contains("FROM meta.consumption_outbound co"));
        assertTrue(outboundQuery.contains("JOIN meta.object_catalog o ON o.id = co.object_id"));
        assertTrue(outboundQuery.contains("JOIN meta.data_connection dc ON dc.connection_id = o.connection_id"));
        assertTrue(outboundQuery.contains("JOIN meta.consumption_outbound_grain cog ON cog.client_id = co.client_id AND cog.outbound_id = co.id"));
        assertTrue(outboundQuery.contains("JOIN meta.attribute_catalog a ON a.schema_cd = o.schema_cd AND a.object_cd = o.object_cd AND a.attribute_cd = cog.logical_attribute_cd"));
        assertTrue(outboundQuery.contains("COALESCE(lno.override_nm, a.physical_attribute_nm) AS effective_logical_attribute_nm"));
        assertTrue(outboundQuery.contains("co.id AS outbound_id"));
        assertTrue(outboundQuery.contains("co.outbound_cd"));
        assertTrue(outboundQuery.contains("cog.grain_level_nbr"));
        assertTrue(outboundQuery.contains("o.source_object_nm"));
        assertTrue(outboundQuery.contains("dc.engine_cd"));
        assertTrue(outboundQuery.contains("a.canonical_data_type_cd AS data_type_cd"));
        assertTrue(outboundQuery.contains("co.id = :outbound_id"));
        assertTrue(outboundQuery.contains("ORDER BY CASE WHEN co.client_id = :client_id THEN 0 ELSE 1 END, cog.grain_level_nbr, cog.logical_attribute_cd"));
        assertFalse(outboundQuery.contains("INSERT INTO"));
        assertFalse(outboundQuery.contains("UPDATE "));
        assertFalse(outboundQuery.contains("DELETE FROM"));
    }
}
