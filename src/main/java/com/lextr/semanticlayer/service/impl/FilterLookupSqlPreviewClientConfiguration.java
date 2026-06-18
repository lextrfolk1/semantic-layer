package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.service.FilterLookupSqlPreviewClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterLookupSqlPreviewClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(FilterLookupSqlPreviewClient.class)
    FilterLookupSqlPreviewClient filterLookupSqlPreviewClient(FilterLookupReadDao filterLookupReadDao) {
        return new JdbcFilterLookupSqlPreviewClient(filterLookupReadDao);
    }
}
