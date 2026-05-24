package com.apipilot.authserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document metadata for the auth-server. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("auth-server API")
                .version("0.1.0")
                .description("OAuth2 Authorization Server for the API Gateway Pilot prototype. "
                        + "The OAuth2 and OIDC protocol endpoints are provided by Spring "
                        + "Authorization Server under /oauth2/** and /.well-known/**."));
    }
}
