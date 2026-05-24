package com.apipilot.resourceapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application against a real PostgreSQL container, exercising
 * Flyway migrations and the resource-server wiring.
 *
 * <p>Requires Docker. It runs in CI; locally use {@code mvn package -DskipTests}
 * if Docker is unavailable.
 */
@SpringBootTest
@Testcontainers
class ResourceApiApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
    }
}
