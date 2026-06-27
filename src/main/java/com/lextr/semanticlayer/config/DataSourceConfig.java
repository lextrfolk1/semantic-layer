package com.lextr.semanticlayer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
@ConditionalOnProperty(prefix = "app.datasource", name = "primary")
public class DataSourceConfig {

    public static final String PRIMARY_DATA_SOURCE = "primaryDataSource";
    public static final String CLICKHOUSE_DATA_SOURCE = "clickHouseDataSource";
    private static final String DB_ENGINE_CLICKHOUSE = "CLICKHOUSE";
    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    private final Map<String, DataSource> dataSources = new LinkedHashMap<>();
    private final String primaryDataSourceName;

    public DataSourceConfig(DataSourceProperties properties,
                            @Value("${data_db_engine:JDBC}") String dataDbEngine) {
        this.primaryDataSourceName = properties.getPrimary();
        boolean clickHouseEnabled = isClickHouseEnabled(dataDbEngine);

        properties.getDataSources().forEach((name, config) -> {
            if (CLICKHOUSE_DATA_SOURCE.equals(name) && !clickHouseEnabled) {
                logger.info("Skipping ClickHouse datasource because data_db_engine='{}'", dataDbEngine);
                return;
            }
            dataSources.put(name, createHikariDataSource(name, config));
        });
    }

    public DataSource getDataSource(String name) {
        return dataSources.get(name);
    }

    @Bean(name = PRIMARY_DATA_SOURCE)
    @Primary
    public DataSource primaryDataSource() {
        if (!StringUtils.hasText(primaryDataSourceName)) {
            throw new IllegalStateException("app.datasource.primary is not configured");
        }

        DataSource dataSource = dataSources.get(primaryDataSourceName);
        if (dataSource == null) {
            throw new IllegalStateException("Primary datasource '" + primaryDataSourceName + "' is not available");
        }
        return dataSource;
    }

    private static DataSource createHikariDataSource(String name, DataSourceProperties.DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(name);
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        if (StringUtils.hasText(config.getDriverClassName())) {
            hikariConfig.setDriverClassName(config.getDriverClassName());
        }
        hikariConfig.setConnectionTimeout(60_000);
        hikariConfig.setValidationTimeout(30_000);

        config.getAdditionalProperties().forEach((key, value) -> applyAdditionalProperty(hikariConfig, key, value));
        return new HikariDataSource(hikariConfig);
    }

    private static boolean isClickHouseEnabled(String dataDbEngine) {
        return StringUtils.hasText(dataDbEngine) && DB_ENGINE_CLICKHOUSE.equalsIgnoreCase(dataDbEngine.trim());
    }

    private static void applyAdditionalProperty(HikariConfig hikariConfig, String key, String value) {
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        switch (normalizedKey) {
            case "maximum-pool-size" -> hikariConfig.setMaximumPoolSize(Integer.parseInt(value));
            case "minimum-idle" -> hikariConfig.setMinimumIdle(Integer.parseInt(value));
            case "pool-name" -> hikariConfig.setPoolName(value);
            case "connection-timeout" -> hikariConfig.setConnectionTimeout(Long.parseLong(value));
            case "validation-timeout" -> hikariConfig.setValidationTimeout(Long.parseLong(value));
            default -> hikariConfig.addDataSourceProperty(key, value);
        }
    }
}
