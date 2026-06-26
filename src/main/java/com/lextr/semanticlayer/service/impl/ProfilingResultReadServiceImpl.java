package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dao.ProfilingResultReadDao;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.dto.ProfilingResultDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.model.ProfilingResultRecord;
import com.lextr.semanticlayer.service.ProfilingResultReadService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProfilingResultReadServiceImpl implements ProfilingResultReadService {

    private final ObjectExposureReadDao objectExposureReadDao;
    private final ProfilingResultReadDao profilingResultReadDao;

    public ProfilingResultReadServiceImpl(ObjectExposureReadDao objectExposureReadDao,
                                         ProfilingResultReadDao profilingResultReadDao) {
        this.objectExposureReadDao = objectExposureReadDao;
        this.profilingResultReadDao = profilingResultReadDao;
    }

    @Override
    public List<ProfilingResultDto> findMetrics(String clientId, UUID objectId, String profilingStatusCode) {
        ObjectExposureRecord object = objectExposureReadDao.findObject(clientId, objectId)
                .orElseThrow(() -> new RegistryResourceNotFoundException("object", objectId.toString()));
        Map<String, String> effectiveAttributeNames = objectExposureReadDao.findAttributes(clientId, objectId).stream()
                .collect(Collectors.toMap(
                        AttributeExposureRecord::attribute_cd,
                        this::effectiveAttributeName,
                        (left, right) -> left
                ));

        return profilingResultReadDao.findMetrics(clientId, object.schema_cd(), object.object_cd(), profilingStatusCode).stream()
                .map(record -> toDto(record, effectiveAttributeNames))
                .toList();
    }

    private ProfilingResultDto toDto(ProfilingResultRecord record, Map<String, String> effectiveAttributeNames) {
        return new ProfilingResultDto(
                record.id(),
                record.client_id(),
                record.schema_cd(),
                record.object_cd(),
                effectiveAttributeNames.getOrDefault(record.logical_attribute_cd(), record.logical_attribute_cd()),
                record.attribute_role_cd(),
                record.null_pct_nbr(),
                record.distinct_pct_nbr(),
                record.profiling_status_cd(),
                record.last_profiled_ts(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private String effectiveAttributeName(AttributeExposureRecord record) {
        if (record.effective_attribute_nm() != null && !record.effective_attribute_nm().isBlank()) {
            return record.effective_attribute_nm();
        }
        if (record.attribute_nm() != null && !record.attribute_nm().isBlank()) {
            return record.attribute_nm();
        }
        return record.attribute_cd();
    }
}
