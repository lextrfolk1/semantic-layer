package com.lextr.semanticlayer.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI semanticLayerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Semantic Layer API")
                .description("OpenAPI documentation for the semantic-layer service.")
                .version("v1")
                .license(new License().name("Proprietary")));
    }
}
