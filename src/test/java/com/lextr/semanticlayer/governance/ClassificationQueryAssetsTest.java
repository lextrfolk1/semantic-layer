package com.lextr.semanticlayer.governance;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassificationQueryAssetsTest {

    @Test
    void loadsClassificationAndAccessQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String classificationListQuery = loader.getQuery("classification_ref.find_all");
        String classificationByCodeQuery = loader.getQuery("classification_ref.find_by_code");
        String attributeAccessGrantQuery = loader.getQuery("attribute_access_grant.find_by_attribute");
        String updateObjectClassificationQuery = loader.getQuery("object_registration.update_object_classification");
        String updateAttributeClassificationQuery = loader.getQuery("object_registration.update_attribute_classification");
        String insertAttributeAccessGrantQuery = loader.getQuery("object_registration.insert_attribute_access_grant");
        String updateAttributeAccessGrantStatusQuery = loader.getQuery("object_registration.update_attribute_access_grant_status");

        assertTrue(classificationListQuery.contains("FROM meta.data_classification_ref"));
        assertTrue(classificationListQuery.contains("data_classification_cd"));
        assertTrue(classificationListQuery.contains("data_classification_nm"));
        assertTrue(classificationListQuery.contains("classification_rank_nbr"));
        assertTrue(classificationListQuery.contains("ai_exposure_default_cd"));
        assertTrue(classificationListQuery.contains("ORDER BY classification_rank_nbr, data_classification_cd"));

        assertTrue(classificationByCodeQuery.contains("FROM meta.data_classification_ref"));
        assertTrue(classificationByCodeQuery.contains("data_classification_cd = :data_classification_cd"));
        assertTrue(classificationByCodeQuery.contains("data_classification_desc"));

        assertTrue(attributeAccessGrantQuery.contains("FROM meta.attribute_access_grant"));
        assertTrue(attributeAccessGrantQuery.contains("WHERE client_id = :client_id"));
        assertTrue(attributeAccessGrantQuery.contains("schema_cd = :schema_cd"));
        assertTrue(attributeAccessGrantQuery.contains("object_cd = :object_cd"));
        assertTrue(attributeAccessGrantQuery.contains("attribute_cd = :attribute_cd"));
        assertTrue(attributeAccessGrantQuery.contains("CAST(:grant_status_cd AS varchar) IS NULL"));
        assertTrue(attributeAccessGrantQuery.contains("grant_status_cd = :grant_status_cd"));
        assertTrue(attributeAccessGrantQuery.contains("role_cd"));
        assertTrue(attributeAccessGrantQuery.contains("purpose_cd"));
        assertTrue(attributeAccessGrantQuery.contains("grant_scope_cd"));
        assertTrue(attributeAccessGrantQuery.contains("approved_by"));
        assertTrue(attributeAccessGrantQuery.contains("ORDER BY role_cd, purpose_cd, grant_scope_cd"));

        assertTrue(updateObjectClassificationQuery.contains("UPDATE meta.object_catalog"));
        assertTrue(updateObjectClassificationQuery.contains("data_classification_cd = :data_classification_cd"));
        assertTrue(updateObjectClassificationQuery.contains("client_id = :client_id"));
        assertTrue(updateAttributeClassificationQuery.contains("UPDATE meta.attribute_catalog a"));
        assertTrue(updateAttributeClassificationQuery.contains("masking_policy_cd = :masking_policy_cd"));
        assertTrue(updateAttributeClassificationQuery.contains("mnpi_flg = :mnpi_flg"));
        assertTrue(updateAttributeClassificationQuery.contains("ai_exposure_cd = :ai_exposure_cd"));
        assertTrue(insertAttributeAccessGrantQuery.contains("INSERT INTO meta.attribute_access_grant"));
        assertTrue(insertAttributeAccessGrantQuery.contains("grant_status_cd"));
        assertTrue(updateAttributeAccessGrantStatusQuery.contains("UPDATE meta.attribute_access_grant"));
        assertTrue(updateAttributeAccessGrantStatusQuery.contains("WHERE client_id = :client_id AND id = :id"));
    }
}
