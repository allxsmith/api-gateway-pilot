package com.apipilot.resourceapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Resource server for the API Gateway Pilot prototype.
 *
 * <p>Serves user and device information to the React SPA. Requests are
 * authenticated with JWT access tokens issued by the auth-server; tokens are
 * validated against the auth-server's published JWK set.
 */
@SpringBootApplication
public class ResourceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceApiApplication.class, args);
    }
}
