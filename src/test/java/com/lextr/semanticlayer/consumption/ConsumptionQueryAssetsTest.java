package com.lextr.semanticlayer.consumption;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumptionQueryAssetsTest {

    @Test
    void loadsConsumptionLayerReadAndPromotionQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String layerInsertQuery = loader.getQuery("consumption_layer.insert_request");
        String layerListQuery = loader.getQuery("consumption_layer.find_all");
        String layerByCodeQuery = loader.getQuery("consumption_layer.find_by_code");
        String outboundInsertQuery = loader.getQuery("consumption_outbound.insert_request");
        String outboundGrainInsertQuery = loader.getQuery("consumption_outbound_grain.insert_request");
        String outboundListQuery = loader.getQuery("consumption_outbound.find_all_by_object");
        String outboundByIdQuery = loader.getQuery("consumption_outbound.find_by_id");
        String outboundByIdUnscopedQuery = loader.getQuery("consumption_outbound.find_by_id_unscoped");
        String outboundGrainQuery = loader.getQuery("consumption_outbound_grain.find_by_outbound");
        String promotionListQuery = loader.getQuery("consumption_promotion.find_by_outbound");

        assertTrue(layerInsertQuery.contains("INSERT INTO meta.consumption_layer"));
        assertTrue(layerInsertQuery.contains(":client_id"));
        assertTrue(layerInsertQuery.contains(":layer_cd"));
        assertTrue(layerInsertQuery.contains(":layer_nm"));
        assertTrue(layerInsertQuery.contains(":layer_type_cd"));

        assertTrue(layerListQuery.contains("FROM meta.consumption_layer"));
        assertTrue(layerListQuery.contains("layer_cd"));
        assertTrue(layerListQuery.contains("layer_nm"));
        assertTrue(layerListQuery.contains("layer_desc_txt"));
        assertTrue(layerListQuery.contains("layer_type_cd"));
        assertTrue(layerListQuery.contains("client_id = :client_id"));
        assertTrue(layerListQuery.contains("client_id = 'GLOBAL'"));
        assertTrue(layerListQuery.contains("lifecycle_status_cd"));
        assertTrue(layerListQuery.contains("ORDER BY CASE WHEN client_id = :client_id THEN 0 ELSE 1 END, layer_cd"));

        assertTrue(layerByCodeQuery.contains("layer_cd = :layer_cd"));
        assertTrue(layerByCodeQuery.contains("LIMIT 1"));

        assertTrue(outboundInsertQuery.contains("INSERT INTO meta.consumption_outbound"));
        assertTrue(outboundInsertQuery.contains(":layer_cd"));
        assertTrue(outboundInsertQuery.contains(":object_id"));
        assertTrue(outboundInsertQuery.contains(":outbound_cd"));
        assertTrue(outboundInsertQuery.contains(":version_nbr"));

        assertTrue(outboundGrainInsertQuery.contains("INSERT INTO meta.consumption_outbound_grain"));
        assertTrue(outboundGrainInsertQuery.contains(":outbound_id"));
        assertTrue(outboundGrainInsertQuery.contains(":grain_level_nbr"));
        assertTrue(outboundGrainInsertQuery.contains(":logical_attribute_cd"));

        assertTrue(outboundListQuery.contains("FROM meta.consumption_outbound co"));
        assertTrue(outboundListQuery.contains("co.object_id = :object_id"));
        assertTrue(outboundListQuery.contains("co.outbound_cd"));
        assertTrue(outboundListQuery.contains("co.outbound_nm"));
        assertTrue(outboundListQuery.contains("co.structure_type_cd"));
        assertTrue(outboundListQuery.contains("co.description_txt"));
        assertTrue(outboundListQuery.contains("attributes_jsonb"));
        assertTrue(outboundListQuery.contains("co.sdlc_status_cd"));
        assertTrue(outboundListQuery.contains(":structure_type_cd"));
        assertTrue(outboundListQuery.contains("GROUP BY co.id, co.client_id"));
        assertTrue(outboundListQuery.contains("ORDER BY CASE WHEN co.client_id = :client_id THEN 0 ELSE 1 END, co.outbound_cd"));

        assertTrue(outboundByIdQuery.contains("co.id = :outbound_id"));
        assertTrue(outboundByIdQuery.contains("LIMIT 1"));
        assertTrue(outboundByIdUnscopedQuery.contains("FROM meta.consumption_outbound co"));
        assertTrue(outboundByIdUnscopedQuery.contains("co.id = :outbound_id"));
        assertTrue(outboundByIdUnscopedQuery.contains("LIMIT 1"));

        assertTrue(outboundGrainQuery.contains("FROM meta.consumption_outbound_grain"));
        assertTrue(outboundGrainQuery.contains("outbound_id = :outbound_id"));
        assertTrue(outboundGrainQuery.contains("grain_level_nbr"));
        assertTrue(outboundGrainQuery.contains("logical_attribute_cd"));
        assertTrue(outboundGrainQuery.contains("attribute_role_cd"));
        assertTrue(outboundGrainQuery.contains("ORDER BY CASE WHEN client_id = :client_id THEN 0 ELSE 1 END, grain_level_nbr, logical_attribute_cd"));

        assertTrue(promotionListQuery.contains("FROM meta.consumption_promotion"));
        assertTrue(promotionListQuery.contains("outbound_id = :outbound_id"));
        assertTrue(promotionListQuery.contains("source_sdlc_status_cd"));
        assertTrue(promotionListQuery.contains("target_sdlc_status_cd"));
        assertTrue(promotionListQuery.contains("validation_status_cd"));
        assertTrue(promotionListQuery.contains("opa_decision_cd"));
        assertTrue(promotionListQuery.contains("workflow_task_id"));
        assertTrue(promotionListQuery.contains("promotion_status_cd"));
        assertTrue(promotionListQuery.contains("version_nbr"));
        assertTrue(promotionListQuery.contains("applied_ts"));
        assertTrue(promotionListQuery.contains("ORDER BY CASE WHEN client_id = :client_id THEN 0 ELSE 1 END, version_nbr DESC, id DESC"));
    }

    @Test
    void loadsConsumptionPromotionWriteQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String insertRequestQuery = loader.getQuery("consumption_promotion.insert_request");
        String applyPromotionQuery = loader.getQuery("consumption_promotion.apply_promotion");
        String workflowTaskQuery = loader.getQuery("consumption_promotion.insert_workflow_task");
        String historyQuery = loader.getQuery("consumption_promotion.insert_metadata_change_history");

        assertTrue(insertRequestQuery.contains("INSERT INTO meta.consumption_promotion"));
        assertTrue(insertRequestQuery.contains(":client_id"));
        assertTrue(insertRequestQuery.contains(":outbound_id"));
        assertTrue(insertRequestQuery.contains(":source_sdlc_status_cd"));
        assertTrue(insertRequestQuery.contains(":target_sdlc_status_cd"));
        assertTrue(insertRequestQuery.contains(":validation_status_cd"));
        assertTrue(insertRequestQuery.contains(":opa_decision_cd"));
        assertTrue(insertRequestQuery.contains(":workflow_task_id"));
        assertTrue(insertRequestQuery.contains(":promotion_status_cd"));
        assertTrue(insertRequestQuery.contains(":version_nbr"));
        assertTrue(insertRequestQuery.contains("RETURNING id, client_id, outbound_id"));
        assertTrue(insertRequestQuery.contains("applied_ts"));
        assertTrue(insertRequestQuery.contains("applied_by"));

        assertTrue(applyPromotionQuery.contains("UPDATE meta.consumption_promotion"));
        assertTrue(applyPromotionQuery.contains("version_nbr = version_nbr + 1"));
        assertTrue(applyPromotionQuery.contains(":target_sdlc_status_cd"));
        assertTrue(applyPromotionQuery.contains(":validation_status_cd"));
        assertTrue(applyPromotionQuery.contains(":opa_decision_cd"));
        assertTrue(applyPromotionQuery.contains(":promotion_status_cd"));
        assertTrue(applyPromotionQuery.contains(":applied_ts"));
        assertTrue(applyPromotionQuery.contains("WHERE client_id = :client_id AND id = :id"));
        assertTrue(applyPromotionQuery.contains("RETURNING id, client_id, outbound_id"));

        assertTrue(workflowTaskQuery.contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(workflowTaskQuery.contains("CONSUMPTION_PROMOTE"));
        assertTrue(workflowTaskQuery.contains(":entity_type_cd"));
        assertTrue(workflowTaskQuery.contains(":entity_ref"));
        assertTrue(workflowTaskQuery.contains(":client_id"));
        assertTrue(workflowTaskQuery.contains("RETURNING id, task_type_cd, entity_type_cd"));

        assertTrue(historyQuery.contains("INSERT INTO meta.metadata_change_history"));
        assertTrue(historyQuery.contains(":entity_type_cd"));
        assertTrue(historyQuery.contains(":entity_ref"));
        assertTrue(historyQuery.contains(":change_type_cd"));
        assertTrue(historyQuery.contains(":change_reason_txt"));
        assertTrue(historyQuery.contains(":changed_by"));
        assertTrue(historyQuery.contains(":changed_ts"));
        assertTrue(historyQuery.contains("RETURNING CAST(md5(id::text) AS uuid) AS change_history_id"));
    }
}
