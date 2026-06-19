package com.lextr.semanticlayer.config;

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

    public static final String PRIMARY_JDBC_TEMPLATE = "primaryJdbcTemplate";
    public static final String SEMANTIC_LAYER_TRANSACTION_OPERATIONS = "semanticLayerTransactionOperations";

    @Bean(name = PRIMARY_JDBC_TEMPLATE)
    @Primary
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(NamedParameterJdbcTemplate.class)
    public NamedParameterJdbcTemplate primaryJdbcTemplate(DataSource primaryDataSource) {
        return new NamedParameterJdbcTemplate(primaryDataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager semanticLayerTransactionManager(DataSource primaryDataSource) {
        return new DataSourceTransactionManager(primaryDataSource);
    }

    @Bean(name = SEMANTIC_LAYER_TRANSACTION_OPERATIONS)
    @ConditionalOnBean(PlatformTransactionManager.class)
    @ConditionalOnMissingBean(name = SEMANTIC_LAYER_TRANSACTION_OPERATIONS)
    public TransactionOperations semanticLayerTransactionOperations(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
