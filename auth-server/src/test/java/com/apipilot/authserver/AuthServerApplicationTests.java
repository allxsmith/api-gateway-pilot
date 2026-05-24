package com.apipilot.authserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application against a real PostgreSQL container. This exercises
 * Flyway migrations, the Authorization Server wiring, and demo-data seeding.
 *
 * <p>Requires Docker. It runs in CI; locally use {@code mvn package -DskipTests}
 * if Docker is unavailable.
 */
@SpringBootTest
@Testcontainers
class AuthServerApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Verifies the context starts, migrations apply, and demo data seeds.
    }
}
