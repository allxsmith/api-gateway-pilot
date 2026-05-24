package com.apipilot.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OAuth2 Authorization Server for the API Gateway Pilot prototype.
 *
 * <p>Issues JWT access and refresh tokens, handles form login, and supports
 * the Authorization Code + PKCE flow used by the React SPA. OAuth clients are
 * read from the legacy {@code oauth_client_details} table.
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
