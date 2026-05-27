---
slug: tutorial-part-4
title: "Part 4: resource-api — the resource server"
authors: [asmith]
tags: [tutorial, series]
date: 2026-05-17T10:00
---

The second service. resource-api validates the JWTs that auth-server issues
and serves the user and device data the dashboard will show. It never sees a
password and never holds a session — every request carries a bearer token,
and validation is local against auth-server's public keys.

{/* truncate */}

## The whole journey

| # | What you'll do |
|---|---|
| 1 | Set up the monorepo |
| 2 | A Docusaurus docs site |
| 3 | auth-server — OAuth2 Authorization Server with a legacy client table |
| 4 | **resource-api — the resource server** *(you are here)* |
| 5 | nginx reverse proxy |
| 6 | The React SPA |
| 7 | Terraform infrastructure |
| 8 | GitHub Actions — CI and CD |
| 9 | AWS account setup |
| 10 | First deploy and teardown |

## Prerequisites

- Part 3 done — auth-server is running and seeded.
- Same toolchain as before.

## What you'll build

- A `resource-api/` Maven module.
- A Spring Boot 3.5 service that:
  - Validates JWT bearer tokens via auth-server's JWKS endpoint.
  - Exposes `GET /api/me`, `GET /api/devices`, and `POST /api/devices`.
  - Owns the `app` schema in the shared database (`auth-server` owns `auth`).
  - Has CORS enabled for the SPA origin.
- Flyway schema + demo seed data so the dashboard has something to show.
- A multi-stage `Dockerfile` and an entry in `docker-compose.yml`.

## Step 1 — Add the module to the parent POM

Edit `pom.xml` at the repo root:

```xml
<modules>
    <module>auth-server</module>
    <module>resource-api</module>
</modules>
```

And update `auth-server/Dockerfile` — add the `resource-api/pom.xml` COPY line
so the reactor can read it:

```dockerfile
COPY pom.xml ./
COPY auth-server/pom.xml auth-server/pom.xml
COPY resource-api/pom.xml resource-api/pom.xml
COPY auth-server/src auth-server/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl auth-server -am -DskipTests clean package
```

## Step 2 — Module POM

Create `resource-api/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.apipilot</groupId>
        <artifactId>api-gateway-pilot</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>resource-api</artifactId>
    <name>resource-api</name>
    <description>Resource server — serves user and device information</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>resource-api</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```

## Step 3 — Main class

Create `resource-api/src/main/java/com/apipilot/resourceapi/ResourceApiApplication.java`:

```java
package com.apipilot.resourceapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Resource server. Validates JWT bearer tokens issued by auth-server and
 * serves the user / device data the SPA shows.
 */
@SpringBootApplication
public class ResourceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceApiApplication.class, args);
    }
}
```

## Step 4 — Flyway schema and demo seed

Create `resource-api/src/main/resources/db/migration/V1__app_schema.sql`:

```sql
-- Profile information shown on the dashboard. Correlated to auth.users by
-- username (the JWT subject) — no cross-schema foreign key, so the services
-- stay independently deployable.
CREATE TABLE user_info (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username   VARCHAR(100) NOT NULL UNIQUE,
    full_name  VARCHAR(150),
    email      VARCHAR(255),
    phone      VARCHAR(40),
    department VARCHAR(100),
    job_title  VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Devices a user has signed in from. Recorded by the SPA after login.
CREATE TABLE device_info (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_info_id  BIGINT NOT NULL REFERENCES user_info (id),
    device_name   VARCHAR(150) NOT NULL,
    device_type   VARCHAR(50),
    os            VARCHAR(80),
    browser       VARCHAR(80),
    last_seen_at  TIMESTAMPTZ,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_info_id, device_name)
);
```

The `UNIQUE (user_info_id, device_name)` constraint backs the upsert in
`DeviceRepository.recordDevice` — the SPA re-registering the same device
just refreshes `last_seen_at`.

Create `resource-api/src/main/resources/db/migration/V2__seed_demo_data.sql`:

```sql
-- Demo profiles and devices, matching the demo users seeded by auth-server.
INSERT INTO user_info (username, full_name, email, phone, department, job_title) VALUES
    ('alice', 'Alice Anderson', 'alice@example.com', '+1-555-0101', 'Platform Engineering', 'Staff Engineer'),
    ('bob',   'Bob Brown',      'bob@example.com',   '+1-555-0102', 'Security',             'Security Analyst');

INSERT INTO device_info (user_info_id, device_name, device_type, os, browser, last_seen_at) VALUES
    ((SELECT id FROM user_info WHERE username = 'alice'), 'Alice MacBook Pro', 'laptop', 'macOS 15',     'Chrome',  now()),
    ((SELECT id FROM user_info WHERE username = 'alice'), 'Alice iPhone',      'phone',  'iOS 19',       'Safari',  now() - INTERVAL '2 days'),
    ((SELECT id FROM user_info WHERE username = 'bob'),   'Bob ThinkPad',      'laptop', 'Ubuntu 24.04', 'Firefox', now());
```

## Step 5 — Application config

Create `resource-api/src/main/resources/application.yml`:

```yaml
server:
  port: ${RESOURCE_API_PORT:8080}
  forward-headers-strategy: framework

spring:
  application:
    name: resource-api
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:apipilot}
    username: ${DB_USERNAME:apipilot}
    password: ${DB_PASSWORD:apipilot}
    hikari:
      # resource-api owns the "app" schema within the shared database.
      connection-init-sql: SET search_path TO app, public
  flyway:
    enabled: true
    schemas: app
    default-schema: app
    create-schemas: true
  security:
    oauth2:
      resourceserver:
        jwt:
          # Public keys are fetched from the auth-server to validate tokens.
          jwk-set-uri: ${JWK_SET_URI:http://localhost:9000/oauth2/jwks}

app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```

The key line is `jwk-set-uri`. Spring's resource server fetches that URL once
to get auth-server's public keys, then validates JWT signatures locally —
**no call back to auth-server per request**. That's the whole point of
splitting an identity service from a resource service.

## Step 6 — Security config

Create `resource-api/src/main/java/com/apipilot/resourceapi/config/SecurityConfig.java`:

```java
package com.apipilot.resourceapi.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT resource-server security.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // CSRF is not needed: this is a stateless, token-authenticated API.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

## Step 7 — OpenAPI metadata

Create `resource-api/src/main/java/com/apipilot/resourceapi/config/OpenApiConfig.java`:

```java
package com.apipilot.resourceapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI resourceApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("resource-api API")
                        .version("0.1.0")
                        .description("Serves user and device information. All endpoints require a "
                                + "JWT access token issued by the auth-server."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

## Step 8 — The user endpoints

Create `resource-api/src/main/java/com/apipilot/resourceapi/user/UserInfo.java`:

```java
package com.apipilot.resourceapi.user;

import org.springframework.security.oauth2.jwt.Jwt;

/** A user's profile, as returned by {@code GET /api/me}. */
public record UserInfo(
        String username,
        String fullName,
        String email,
        String phone,
        String department,
        String jobTitle) {

    /**
     * Builds a minimal profile from the access token, for an authenticated user
     * who has no {@code user_info} row yet.
     */
    public static UserInfo fromJwt(Jwt jwt) {
        return new UserInfo(jwt.getSubject(), null, null, null, null, null);
    }
}
```

Create `resource-api/src/main/java/com/apipilot/resourceapi/user/UserInfoRepository.java`:

```java
package com.apipilot.resourceapi.user;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads and provisions rows in the {@code user_info} table. */
@Repository
public class UserInfoRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserInfoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserInfo> findByUsername(String username) {
        return jdbcTemplate.query("""
                        SELECT username, full_name, email, phone, department, job_title
                        FROM user_info
                        WHERE username = ?
                        """,
                (rs, rowNum) -> new UserInfo(
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("department"),
                        rs.getString("job_title")),
                username).stream().findFirst();
    }

    /** Returns the id of the user_info row for the username, inserting one if missing. */
    public long ensureUser(String username) {
        Long existing = jdbcTemplate.query(
                "SELECT id FROM user_info WHERE username = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                username);
        if (existing != null) {
            return existing;
        }
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO user_info (username) VALUES (?) RETURNING id",
                Long.class, username);
        return id;
    }
}
```

Create `resource-api/src/main/java/com/apipilot/resourceapi/user/UserController.java`:

```java
package com.apipilot.resourceapi.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "User", description = "The authenticated user's profile")
public class UserController {

    private final UserInfoRepository userInfoRepository;

    public UserController(UserInfoRepository userInfoRepository) {
        this.userInfoRepository = userInfoRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's profile")
    public UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        return userInfoRepository.findByUsername(username)
                .orElseGet(() -> UserInfo.fromJwt(jwt));
    }
}
```

`@AuthenticationPrincipal Jwt jwt` is Spring Security's resource-server
plumbing handing you the decoded JWT — the `sub` claim is the username.

## Step 9 — The device endpoints

Create four files in `resource-api/src/main/java/com/apipilot/resourceapi/device/`.

`DeviceInfo.java`:

```java
package com.apipilot.resourceapi.device;

import java.time.Instant;

public record DeviceInfo(
        String deviceName,
        String deviceType,
        String os,
        String browser,
        Instant lastSeenAt,
        Instant registeredAt) {
}
```

`RegisterDeviceRequest.java`:

```java
package com.apipilot.resourceapi.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank @Size(max = 150) String deviceName,
        @Size(max = 50) String deviceType,
        @Size(max = 80) String os,
        @Size(max = 80) String browser) {
}
```

`DeviceRepository.java`:

<details>
<summary>DeviceRepository.java — listing and upserting devices</summary>

```java
package com.apipilot.resourceapi.device;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.apipilot.resourceapi.user.UserInfoRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbcTemplate;
    private final UserInfoRepository userInfoRepository;

    public DeviceRepository(JdbcTemplate jdbcTemplate, UserInfoRepository userInfoRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userInfoRepository = userInfoRepository;
    }

    public List<DeviceInfo> findByUsername(String username) {
        return jdbcTemplate.query("""
                        SELECT d.device_name, d.device_type, d.os, d.browser,
                               d.last_seen_at, d.registered_at
                        FROM device_info d
                        JOIN user_info u ON u.id = d.user_info_id
                        WHERE u.username = ?
                        ORDER BY d.last_seen_at DESC NULLS LAST
                        """,
                (rs, rowNum) -> mapDevice(rs), username);
    }

    public DeviceInfo recordDevice(String username, RegisterDeviceRequest request) {
        long userInfoId = userInfoRepository.ensureUser(username);
        jdbcTemplate.update("""
                        INSERT INTO device_info
                          (user_info_id, device_name, device_type, os, browser, last_seen_at)
                        VALUES (?, ?, ?, ?, ?, now())
                        ON CONFLICT (user_info_id, device_name) DO UPDATE SET
                          device_type = excluded.device_type,
                          os = excluded.os,
                          browser = excluded.browser,
                          last_seen_at = now()
                        """,
                userInfoId, request.deviceName(), request.deviceType(),
                request.os(), request.browser());
        return jdbcTemplate.queryForObject("""
                        SELECT device_name, device_type, os, browser,
                               last_seen_at, registered_at
                        FROM device_info
                        WHERE user_info_id = ? AND device_name = ?
                        """,
                (rs, rowNum) -> mapDevice(rs), userInfoId, request.deviceName());
    }

    private static DeviceInfo mapDevice(ResultSet rs) throws SQLException {
        return new DeviceInfo(
                rs.getString("device_name"),
                rs.getString("device_type"),
                rs.getString("os"),
                rs.getString("browser"),
                toInstant(rs.getTimestamp("last_seen_at")),
                toInstant(rs.getTimestamp("registered_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
```

</details>

`DeviceController.java`:

```java
package com.apipilot.resourceapi.device;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Devices", description = "Devices the user has signed in from")
public class DeviceController {

    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @GetMapping("/devices")
    @Operation(summary = "List the current user's devices")
    public List<DeviceInfo> devices(@AuthenticationPrincipal Jwt jwt) {
        return deviceRepository.findByUsername(jwt.getSubject());
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record the device the user signed in from")
    public DeviceInfo register(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody RegisterDeviceRequest request) {
        return deviceRepository.recordDevice(jwt.getSubject(), request);
    }
}
```

## Step 10 — Dockerfile

Create `resource-api/Dockerfile`:

```dockerfile
# syntax=docker/dockerfile:1
# Build context is the repository root (the module inherits the parent POM).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY auth-server/pom.xml auth-server/pom.xml
COPY resource-api/pom.xml resource-api/pom.xml
COPY resource-api/src resource-api/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl resource-api -am -DskipTests clean package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system spring \
 && useradd --system --gid spring spring
USER spring
COPY --from=build /workspace/resource-api/target/resource-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

Same pattern as auth-server: copy every module's POM but only this service's
`src/`.

## Step 11 — Wire into docker-compose

Append the `resource-api` service to `docker-compose.yml`. The full file is
now:

```yaml
name: api-gateway-pilot

services:
  postgres:
    image: postgres:16-alpine
    container_name: agp-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-apipilot}
      POSTGRES_USER: ${POSTGRES_USER:-apipilot}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-apipilot}
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-apipilot} -d ${POSTGRES_DB:-apipilot}"]
      interval: 5s
      timeout: 5s
      retries: 10

  auth-server:
    build:
      context: .
      dockerfile: auth-server/Dockerfile
    container_name: agp-auth-server
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${POSTGRES_DB:-apipilot}
      DB_USERNAME: ${POSTGRES_USER:-apipilot}
      DB_PASSWORD: ${POSTGRES_PASSWORD:-apipilot}
      AUTH_SERVER_PORT: 9000
      AUTH_ISSUER_URI: http://localhost:9000
      CORS_ALLOWED_ORIGINS: http://localhost:5173
    ports:
      - "${AUTH_SERVER_PORT:-9000}:9000"
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:9000/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s

  resource-api:
    build:
      context: .
      dockerfile: resource-api/Dockerfile
    container_name: agp-resource-api
    depends_on:
      postgres:
        condition: service_healthy
      auth-server:
        condition: service_healthy
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ${POSTGRES_DB:-apipilot}
      DB_USERNAME: ${POSTGRES_USER:-apipilot}
      DB_PASSWORD: ${POSTGRES_PASSWORD:-apipilot}
      RESOURCE_API_PORT: 8080
      # resource-api reaches auth-server by its Compose service name.
      JWK_SET_URI: http://auth-server:9000/oauth2/jwks
      CORS_ALLOWED_ORIGINS: http://localhost:5173
    ports:
      - "${RESOURCE_API_PORT:-8080}:8080"
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s

volumes:
  pgdata:
```

## Step 12 — Context-load test

Create `resource-api/src/test/java/com/apipilot/resourceapi/ResourceApiApplicationTests.java`:

```java
package com.apipilot.resourceapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
```

## Verify

```sh
make up
make ps
```

After ~60s both `agp-auth-server` and `agp-resource-api` should be
`(healthy)`. Then prove the end-to-end token validation works:

```sh
# Get a client_credentials token from auth-server (one line):
TOKEN=$(curl -s -u resource-api:resource-api-secret \
  -d 'grant_type=client_credentials&scope=internal.read' \
  http://localhost:9000/oauth2/token | jq -r .access_token)

# Hit resource-api with it (the `sub` claim is "resource-api", so there's no
# user_info row for it — you get a minimal profile back):
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me | jq
```

You should get something like:

```json
{
  "username": "resource-api",
  "fullName": null,
  "email": null,
  ...
}
```

And without a token:

```sh
curl -i http://localhost:8080/api/me | head -3
# HTTP/1.1 401
```

Look up Alice's devices (you'll need a token with `sub: alice` from the
authorization-code flow — that's coming when the SPA lands in Part 6; for now
querying the DB directly is the way to see the seed data):

```sh
make db
# At apipilot=#
SELECT username, device_name, os FROM app.device_info d JOIN app.user_info u ON u.id = d.user_info_id;
```

## Update local-development.md

Mark resource-api as added today:

```md
:::note
Today `make up` starts PostgreSQL, the auth-server, and the resource-api.
:::
```

## Commit

```sh
git add -A
git commit -m "feat: resource server for user and device info"
```

## What's next

**Part 5 — nginx reverse proxy.** A single front door for both services so
the React SPA in Part 6 talks to one origin, with a careful note about the
proxy-header trap that breaks OAuth redirects if you get it wrong.
