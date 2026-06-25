package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.AttributeExposureDto;
import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ObjectExposureReadServiceImpl implements ObjectExposureReadService {

    private final ObjectExposureReadDao objectExposureReadDao;

    public ObjectExposureReadServiceImpl(ObjectExposureReadDao objectExposureReadDao) {
        this.objectExposureReadDao = objectExposureReadDao;
    }

    @Override
    public List<ObjectExposureSummaryDto> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
        return objectExposureReadDao.findObjects(clientId, schemaCode, lifecycleStatusCode).stream()
                .map(this::toSummaryDto)
                .toList();
    }

    @Override
    public ObjectExposureDetailDto findObject(String clientId, UUID objectId) {
        ObjectExposureRecord object = objectExposureReadDao.findObject(clientId, objectId)
                .orElseThrow(() -> new RegistryResourceNotFoundException("object", objectId.toString()));
        List<AttributeExposureDto> attributes = objectExposureReadDao.findAttributes(clientId, objectId).stream()
                .map(this::toAttributeDto)
                .toList();
        return toDetailDto(object, attributes);
    }

    private ObjectExposureSummaryDto toSummaryDto(ObjectExposureRecord record) {
        return new ObjectExposureSummaryDto(
                record.object_id(),
                record.object_cd(),
                effectiveValue(record.effective_object_nm(), record.object_nm()),
                record.object_type_cd(),
                record.schema_cd(),
                record.connection_id(),
                record.lifecycle_status_cd(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private ObjectExposureDetailDto toDetailDto(ObjectExposureRecord record, List<AttributeExposureDto> attributes) {
        return new ObjectExposureDetailDto(
                record.object_id(),
                record.object_cd(),
                effectiveValue(record.effective_object_nm(), record.object_nm()),
                record.object_type_cd(),
                record.schema_cd(),
                record.connection_id(),
                record.lifecycle_status_cd(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by(),
                attributes
        );
    }

    private AttributeExposureDto toAttributeDto(AttributeExposureRecord record) {
        return new AttributeExposureDto(
                record.attribute_id(),
                record.attribute_cd(),
                effectiveValue(record.effective_attribute_nm(), record.attribute_nm()),
                record.data_type_cd(),
                record.taxonomy_cd(),
                record.taxonomy_source_cd(),
                record.taxonomy_jurisdiction_cd(),
                record.pk_flg(),
                record.fk_flg(),
                record.nullable_flg(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private String effectiveValue(String effectiveValue, String baseValue) {
        return effectiveValue == null || effectiveValue.isBlank() ? baseValue : effectiveValue;
    }
}
