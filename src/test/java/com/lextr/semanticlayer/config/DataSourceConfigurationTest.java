package com.lextr.semanticlayer.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataSourceConfig.class, JdbcTemplateConfig.class);

    @Test
    void configurationPropertiesBindCorrectlyToAppDatasource() {
        contextRunner
                .withPropertyValues(
                        "app.datasource.primary=primaryDataSource",
                        "app.datasource.datasources.primaryDataSource.url=jdbc:h2:mem:bind-primary;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.primaryDataSource.username=sa",
                        "app.datasource.datasources.primaryDataSource.password=",
                        "app.datasource.datasources.primaryDataSource.driver-class-name=org.h2.Driver",
                        "app.datasource.datasources.primaryDataSource.additional-properties.maximum-pool-size=7",
                        "app.datasource.datasources.clickHouseDataSource.url=jdbc:h2:mem:bind-clickhouse;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.clickHouseDataSource.username=sa",
                        "app.datasource.datasources.clickHouseDataSource.password=",
                        "app.datasource.datasources.clickHouseDataSource.driver-class-name=org.h2.Driver"
                )
                .run(context -> {
                    DataSourceProperties properties = context.getBean(DataSourceProperties.class);

                    assertEquals("primaryDataSource", properties.getPrimary());
                    assertEquals(
                            "jdbc:h2:mem:bind-primary;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                            properties.getDataSources().get("primaryDataSource").getUrl()
                    );
                    assertEquals(
                            "7",
                            properties.getDataSources().get("primaryDataSource").getAdditionalProperties().get("maximum-pool-size")
                    );
                    assertEquals(
                            "jdbc:h2:mem:bind-clickhouse;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                            properties.getDataSources().get("clickHouseDataSource").getUrl()
                    );
                });
    }

    @Test
    void primaryDatasourceSelectionWorksAsConfigured() {
        contextRunner
                .withPropertyValues(
                        "app.datasource.primary=reportingDataSource",
                        "app.datasource.datasources.primaryDataSource.url=jdbc:h2:mem:primary-default;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.primaryDataSource.username=sa",
                        "app.datasource.datasources.primaryDataSource.password=",
                        "app.datasource.datasources.primaryDataSource.driver-class-name=org.h2.Driver",
                        "app.datasource.datasources.reportingDataSource.url=jdbc:h2:mem:reporting;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.reportingDataSource.username=sa",
                        "app.datasource.datasources.reportingDataSource.password=",
                        "app.datasource.datasources.reportingDataSource.driver-class-name=org.h2.Driver",
                        "app.datasource.datasources.reportingDataSource.additional-properties.pool-name=ReportingPool"
                )
                .run(context -> {
                    DataSourceConfig dataSourceConfig = context.getBean(DataSourceConfig.class);
                    DataSource primaryDataSource = context.getBean(DataSource.class);
                    NamedParameterJdbcTemplate jdbcTemplate = context.getBean(NamedParameterJdbcTemplate.class);

                    assertSame(dataSourceConfig.getDataSource("reportingDataSource"), primaryDataSource);
                    assertSame(primaryDataSource, jdbcTemplate.getJdbcTemplate().getDataSource());
                    assertEquals("ReportingPool", ((HikariDataSource) primaryDataSource).getPoolName());
                });
    }

    @Test
    void clickHouseDatasourceCreationIsSkippedWhenEngineIsNotClickhouse() {
        contextRunner
                .withPropertyValues(
                        "data_db_engine=JDBC",
                        "app.datasource.primary=primaryDataSource",
                        "app.datasource.datasources.primaryDataSource.url=jdbc:h2:mem:skip-primary;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.primaryDataSource.username=sa",
                        "app.datasource.datasources.primaryDataSource.password=",
                        "app.datasource.datasources.primaryDataSource.driver-class-name=org.h2.Driver",
                        "app.datasource.datasources.clickHouseDataSource.url=jdbc:h2:mem:skip-clickhouse;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.clickHouseDataSource.username=sa",
                        "app.datasource.datasources.clickHouseDataSource.password=",
                        "app.datasource.datasources.clickHouseDataSource.driver-class-name=org.h2.Driver"
                )
                .run(context -> {
                    DataSourceConfig dataSourceConfig = context.getBean(DataSourceConfig.class);
                    assertNotNull(dataSourceConfig.getDataSource("primaryDataSource"));
                    assertNull(dataSourceConfig.getDataSource("clickHouseDataSource"));
                });
    }

    @Test
    void clickHouseDatasourceCreationIsEnabledWhenEngineIsClickhouse() {
        contextRunner
                .withPropertyValues(
                        "data_db_engine=CLICKHOUSE",
                        "app.datasource.primary=primaryDataSource",
                        "app.datasource.datasources.primaryDataSource.url=jdbc:h2:mem:enable-primary;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.primaryDataSource.username=sa",
                        "app.datasource.datasources.primaryDataSource.password=",
                        "app.datasource.datasources.primaryDataSource.driver-class-name=org.h2.Driver",
                        "app.datasource.datasources.clickHouseDataSource.url=jdbc:h2:mem:enable-clickhouse;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.clickHouseDataSource.username=sa",
                        "app.datasource.datasources.clickHouseDataSource.password=",
                        "app.datasource.datasources.clickHouseDataSource.driver-class-name=org.h2.Driver"
                )
                .run(context -> {
                    DataSourceConfig dataSourceConfig = context.getBean(DataSourceConfig.class);
                    assertNotNull(dataSourceConfig.getDataSource("primaryDataSource"));
                    assertNotNull(dataSourceConfig.getDataSource("clickHouseDataSource"));
                });
    }

    @Test
    void startupFailsWhenConfiguredPrimaryDatasourceIsUnavailable() {
        contextRunner
                .withPropertyValues(
                        "data_db_engine=JDBC",
                        "app.datasource.primary=clickHouseDataSource",
                        "app.datasource.datasources.primaryDataSource.url=jdbc:h2:mem:missing-primary;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.primaryDataSource.username=sa",
                        "app.datasource.datasources.primaryDataSource.password=",
                        "app.datasource.datasources.primaryDataSource.driver-class-name=org.h2.Driver",
                        "app.datasource.datasources.clickHouseDataSource.url=jdbc:h2:mem:missing-clickhouse;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "app.datasource.datasources.clickHouseDataSource.username=sa",
                        "app.datasource.datasources.clickHouseDataSource.password=",
                        "app.datasource.datasources.clickHouseDataSource.driver-class-name=org.h2.Driver"
                )
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(context.getStartupFailure().getMessage().contains("Primary datasource 'clickHouseDataSource' is not available"));
                });
    }

    @Test
    void applicationContextLoadsInTestProfileWithoutRemoteConfigDependency() {
        try (ConfigurableApplicationContext context = run("test")) {
            assertEquals("false", context.getEnvironment().getProperty("spring.cloud.config.enabled"));
            assertEquals("primaryDataSource", context.getEnvironment().getProperty("app.datasource.primary"));
            assertNotNull(context.getBean(DataSource.class));
            assertNotNull(context.getBean(NamedParameterJdbcTemplate.class));
            assertNotNull(context.getBean(JdbcTemplateConfig.SEMANTIC_LAYER_TRANSACTION_OPERATIONS, TransactionOperations.class));
        }
    }

    @Test
    void noJpaIsIntroduced() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertFalse(pom.contains("spring-boot-starter-data-jpa"));
        assertFalse(pom.contains("hibernate-core"));
        assertFalse(pom.contains("jakarta.persistence"));
    }

    private static ConfigurableApplicationContext run(String profile, String... args) {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        if (profile != null) {
            application.setAdditionalProfiles(profile);
        }
        return application.run(args);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            Neo4jAutoConfiguration.class,
            Neo4jDataAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({DataSourceConfig.class, JdbcTemplateConfig.class})
    static class TestApplication {
    }
}
