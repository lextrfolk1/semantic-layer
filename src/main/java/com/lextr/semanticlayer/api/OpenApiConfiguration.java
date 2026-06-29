package com.lextr.semanticlayer.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(OpenApiConfiguration.class);

    @Bean
    OpenAPI semanticLayerOpenApi() {
        logger.debug("Creating OpenAPI configuration bean");
        return new OpenAPI().info(new Info()
                .title("Semantic Layer API")
                .description("OpenAPI documentation for the semantic-layer service.")
                .version("v1")
                .license(new License().name("Proprietary")));
    }
}
