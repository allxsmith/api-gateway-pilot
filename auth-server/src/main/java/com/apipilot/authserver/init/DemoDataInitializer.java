package com.apipilot.authserver.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds demo OAuth clients and users on startup if the tables are empty.
 *
 * <p>Seeding runs in application code (rather than a Flyway migration) so that
 * client secrets and user passwords can be bcrypt-encoded with the same
 * {@link PasswordEncoder} the server uses to verify them. This keeps a fresh
 * database — including after every {@code terraform destroy}/{@code apply} —
 * immediately usable.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String spaRedirectUris;

    public DemoDataInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                               @Value("${app.spa.redirect-uris}") String spaRedirectUris) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.spaRedirectUris = spaRedirectUris;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedClients();
        seedUsers();
    }

    private void seedClients() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM oauth_client_details", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        log.info("Seeding demo OAuth clients");

        // Public SPA client — Authorization Code + PKCE, no secret.
        jdbcTemplate.update("""
                        INSERT INTO oauth_client_details
                          (client_id, client_secret, scope, authorized_grant_types,
                           web_server_redirect_uri, access_token_validity,
                           refresh_token_validity, autoapprove)
                        VALUES (?, NULL, ?, ?, ?, ?, ?, ?)
                        """,
                "spa-client",
                "openid,profile,read",
                "authorization_code,refresh_token",
                spaRedirectUris,
                900, 86400, "true");

        // Confidential service client — client_credentials for service-to-service calls.
        jdbcTemplate.update("""
                        INSERT INTO oauth_client_details
                          (client_id, client_secret, scope, authorized_grant_types,
                           web_server_redirect_uri, access_token_validity,
                           refresh_token_validity, autoapprove)
                        VALUES (?, ?, ?, ?, NULL, ?, NULL, ?)
                        """,
                "resource-api",
                passwordEncoder.encode("resource-api-secret"),
                "internal.read",
                "client_credentials",
                900, "false");
    }

    private void seedUsers() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        log.info("Seeding demo users");
        insertUser("alice", "password", "alice@example.com", "Alice Anderson", "ROLE_USER");
        insertUser("bob", "password", "bob@example.com", "Bob Brown", "ROLE_USER");
    }

    private void insertUser(String username, String rawPassword, String email,
                            String displayName, String authority) {
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO users (username, password_hash, email, display_name, enabled)
                        VALUES (?, ?, ?, ?, TRUE)
                        RETURNING id
                        """, Long.class,
                username, passwordEncoder.encode(rawPassword), email, displayName);
        jdbcTemplate.update(
                "INSERT INTO user_authorities (user_id, authority) VALUES (?, ?)",
                id, authority);
    }
}
