package com.secondshelf.exchangeservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.security.public-docs-enabled", havingValue = "true")
public class OpenApiConfig {

    @Bean
    public OpenAPI exchangeServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Second Shelf Exchange API")
                        .version("v1")
                        .description("Exchange request workflow API. Requesters create requests without selecting their own book; owners can offer a requester book, and requesters finalize the exchange. Contact fields are exposed only when the workflow allows them."))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
