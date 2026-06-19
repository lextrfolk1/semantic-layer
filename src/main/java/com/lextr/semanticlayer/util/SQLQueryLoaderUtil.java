package com.lextr.semanticlayer.util;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Component
public class SQLQueryLoaderUtil {

    static final String DEFAULT_RESOURCE_LOCATION = "classpath:queries.properties";
    private static final Logger logger = LoggerFactory.getLogger(SQLQueryLoaderUtil.class);

    private final Properties queries = new Properties();

    @Autowired
    public SQLQueryLoaderUtil(ResourceLoader resourceLoader) {
        this(resourceLoader, DEFAULT_RESOURCE_LOCATION);
    }

    SQLQueryLoaderUtil(ResourceLoader resourceLoader, String resourceLocation) {
        Resource resource = resourceLoader.getResource(resourceLocation);
        if (!resource.exists()) {
            throw new SemanticLayerException("Unable to load SQL queries from " + resourceLocation);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            queries.load(inputStream);
            logger.info("Loaded {} SQL queries from {}", queries.size(), resourceLocation);
        } catch (IOException exception) {
            throw new SemanticLayerException("Unable to load SQL queries from " + resourceLocation, exception);
        }
    }

    public String getQuery(String key) {
        String query = queries.getProperty(key);
        if (query == null || query.isBlank()) {
            throw new SemanticLayerException("SQL query not found for key: " + key);
        }
        return query;
    }
}
