package com.lextr.semanticlayer.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringCloudConfigConfigurationTest {

    @Test
    void testProfileStartsWithConfigServerDisabled() {
        try (ConfigurableApplicationContext context = run("test")) {
            assertEquals("", context.getEnvironment().getProperty("spring.config.import"));
            assertEquals("false", context.getEnvironment().getProperty("spring.cloud.config.enabled"));
            assertEquals("false", context.getEnvironment().getProperty("spring.cloud.config.fail-fast"));
        }
    }

    @Test
    void mainApplicationPropertiesResolveForLocalDefaults() throws IOException {
        PropertySourcesPropertyResolver resolver = resolverFor(mainApplicationResource(), "main-application.yaml");

        assertEquals("semantic-layer", resolver.getProperty("spring.application.name"));
        assertEquals("optional:configserver:", resolver.getProperty("spring.config.import"));
        assertEquals("true", resolver.getProperty("spring.cloud.config.enabled"));
        assertEquals("http://config-service:8888", resolver.getProperty("spring.cloud.config.uri"));
        assertEquals("false", resolver.getProperty("spring.cloud.config.fail-fast"));
        assertEquals("main", resolver.getProperty("spring.cloud.config.label"));
        assertEquals("INFO", resolver.getProperty("logging.level.com.lextr.semanticlayer"));
    }

    @Test
    void mainConfigConventionsRemainDeclaredWithoutAddingSecondConfigFramework() throws IOException {
        PropertySourcesPropertyResolver resolver = resolverFor(mainApplicationResource(), "main-application.yaml");
        String pom = Files.readString(Path.of("pom.xml"));

        assertEquals("semantic-layer", resolver.getProperty("spring.application.name"));
        assertEquals("optional:configserver:", resolver.getProperty("spring.config.import"));
        assertEquals("http://config-service:8888", resolver.getProperty("spring.cloud.config.uri"));
        assertEquals("false", resolver.getProperty("spring.cloud.config.fail-fast"));
        assertEquals("main", resolver.getProperty("spring.cloud.config.label"));
        assertTrue(pom.contains("<artifactId>spring-cloud-starter-config</artifactId>"));
        assertTrue(pom.contains("<artifactId>spring-cloud-dependencies</artifactId>"));
        assertFalse(pom.contains("spring-cloud-starter-consul-config"));
        assertFalse(pom.contains("spring-cloud-starter-vault-config"));
        assertFalse(pom.contains("spring-cloud-starter-bootstrap"));
    }

    private static ConfigurableApplicationContext run(String profile, String... args) {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        if (profile != null) {
            application.setAdditionalProfiles(profile);
        }
        return application.run(args);
    }

    private static PropertySourcesPropertyResolver resolverFor(Resource resource, String name) throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load(name, resource).forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources);
    }

    private static Resource mainApplicationResource() {
        return new FileSystemResource("src/main/resources/application.yaml");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            JdbcRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            Neo4jAutoConfiguration.class,
            Neo4jDataAutoConfiguration.class
    })
    static class TestApplication {
    }
}
