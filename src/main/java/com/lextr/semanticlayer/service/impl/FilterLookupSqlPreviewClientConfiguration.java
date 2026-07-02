package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.service.FilterLookupSqlPreviewClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterLookupSqlPreviewClientConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FilterLookupSqlPreviewClientConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(FilterLookupSqlPreviewClient.class)
    FilterLookupSqlPreviewClient filterLookupSqlPreviewClient(FilterLookupReadDao filterLookupReadDao) {
        logger.info("Creating JDBC-backed FilterLookupSqlPreviewClient bean");
        return new JdbcFilterLookupSqlPreviewClient(filterLookupReadDao);
    }
}
