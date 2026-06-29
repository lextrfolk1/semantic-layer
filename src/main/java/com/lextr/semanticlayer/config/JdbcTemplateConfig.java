package com.lextr.semanticlayer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class JdbcTemplateConfig {

    private static final Logger logger = LoggerFactory.getLogger(JdbcTemplateConfig.class);

    public static final String PRIMARY_JDBC_TEMPLATE = "primaryJdbcTemplate";
    public static final String SEMANTIC_LAYER_TRANSACTION_OPERATIONS = "semanticLayerTransactionOperations";

    @Bean(name = PRIMARY_JDBC_TEMPLATE)
    @Primary
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(NamedParameterJdbcTemplate.class)
    public NamedParameterJdbcTemplate primaryJdbcTemplate(DataSource primaryDataSource) {
        logger.info("Creating primary NamedParameterJdbcTemplate bean");
        return new NamedParameterJdbcTemplate(primaryDataSource);
    }

    @Bean(name = "semanticLayerTransactionManager")
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(name = "semanticLayerTransactionManager")
    public PlatformTransactionManager semanticLayerTransactionManager(DataSource primaryDataSource) {
        logger.info("Creating semantic layer transaction manager bean");
        return new DataSourceTransactionManager(primaryDataSource);
    }

    @Bean(name = SEMANTIC_LAYER_TRANSACTION_OPERATIONS)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(name = SEMANTIC_LAYER_TRANSACTION_OPERATIONS)
    public TransactionOperations semanticLayerTransactionOperations(@Qualifier("semanticLayerTransactionManager") PlatformTransactionManager transactionManager) {
        logger.info("Creating semantic layer transaction operations bean");
        return new TransactionTemplate(transactionManager);
    }
}
