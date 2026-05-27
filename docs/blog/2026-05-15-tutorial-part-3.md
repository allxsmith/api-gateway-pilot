---
slug: tutorial-part-3
title: "Part 3: auth-server — OAuth2 Authorization Server with a legacy client table"
authors: [asmith]
tags: [tutorial, series]
date: 2026-05-15T10:00
---

The first real service. By the end of Part 3 you'll have an OAuth2
Authorization Server running on `http://localhost:9000`, issuing JWT access
and refresh tokens, with a branded login page and a custom adapter that
reads OAuth clients from the deprecated `oauth_client_details` table — the
schema many older Spring projects still have.

{/* truncate */}

## The whole journey

| # | What you'll do |
|---|---|
| 1 | Set up the monorepo |
| 2 | A Docusaurus docs site |
| 3 | **auth-server — OAuth2 Authorization Server with a legacy client table** *(you are here)* |
| 4 | resource-api — the resource server |
| 5 | nginx reverse proxy |
| 6 | The React SPA |
| 7 | Terraform infrastructure |
| 8 | GitHub Actions — CI and CD |
| 9 | AWS account setup |
| 10 | First deploy and teardown |

## Prerequisites

- Parts 1 and 2 done — monorepo skeleton, Postgres running, docs site building.
- JDK 21, Maven 3.9+, Docker Desktop running.

## What you'll build

- An `auth-server/` Maven module under the parent POM.
- A Spring Boot 3.5 service that:
  - Issues **JWT access and refresh tokens** signed with an RSA key generated
    on startup.
  - Hosts a **custom Thymeleaf login page**.
  - Supports the **Authorization Code + PKCE** flow for the React SPA.
  - Supports **client credentials** for service-to-service.
  - Publishes the **JWKS** so the resource API can validate tokens.
- A `LegacyClientMapper` that turns rows of the deprecated `oauth_client_details`
  table into the modern `RegisteredClient` Spring Authorization Server
  expects.
- Flyway schema for `oauth_client_details` + `users` + `user_authorities`,
  with demo data seeded on first boot.
- A multi-stage `Dockerfile`, wired into `docker-compose.yml`.

## Step 1 — Register the module

Edit `pom.xml` at the repo root. Replace the empty `<modules>` section with:

```xml
<modules>
    <module>auth-server</module>
    <!-- resource-api is added in Part 4. -->
</modules>
```

## Step 2 — Module POM

Create `auth-server/pom.xml`:

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

    <artifactId>auth-server</artifactId>
    <name>auth-server</name>
    <description>OAuth2 Authorization Server — issues JWT tokens and handles login</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
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
        <finalName>auth-server</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```

`spring-boot-starter-oauth2-authorization-server` is the headline dependency
— it brings in Spring Authorization Server 1.5.x, transitively pulled by the
Spring Boot BOM.

## Step 3 — Main class

Create `auth-server/src/main/java/com/apipilot/authserver/AuthServerApplication.java`:

```java
package com.apipilot.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OAuth2 Authorization Server for the API Gateway Pilot prototype.
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
```

## Step 4 — The Flyway schema

Create `auth-server/src/main/resources/db/migration/V1__auth_schema.sql`:

```sql
-- Legacy OAuth client registry. Kept verbatim from the deprecated Spring
-- Security OAuth2 schema so the modern Authorization Server can read it
-- through a custom RegisteredClientRepository.
CREATE TABLE oauth_client_details (
    client_id               VARCHAR(255) PRIMARY KEY,
    resource_ids            VARCHAR(255),
    client_secret           VARCHAR(255),
    scope                   VARCHAR(255),
    authorized_grant_types  VARCHAR(255),
    web_server_redirect_uri VARCHAR(255),
    authorities             VARCHAR(255),
    access_token_validity   INTEGER,
    refresh_token_validity  INTEGER,
    additional_information  VARCHAR(4096),
    autoapprove             VARCHAR(255)
);

-- Application users for form login.
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(150),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_authorities (
    user_id   BIGINT NOT NULL REFERENCES users (id),
    authority VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, authority)
);
```

The `oauth_client_details` columns mirror the deprecated Spring Security
OAuth2 schema. The reason for keeping the table is the whole point of this
part — but you don't have to migrate any data; an empty table works, demo
rows get seeded in Step 11.

## Step 5 — Application config

Create `auth-server/src/main/resources/application.yml`:

```yaml
server:
  port: ${AUTH_SERVER_PORT:9000}
  # Honour X-Forwarded-* headers when running behind the nginx reverse proxy.
  forward-headers-strategy: framework

spring:
  application:
    name: auth-server
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:apipilot}
    username: ${DB_USERNAME:apipilot}
    password: ${DB_PASSWORD:apipilot}
    hikari:
      # auth-server owns the "auth" schema within the shared database.
      connection-init-sql: SET search_path TO auth, public
  flyway:
    enabled: true
    schemas: auth
    default-schema: auth
    create-schemas: true

# Issuer the tokens are minted under. resource-api validates against this value.
app:
  issuer-uri: ${AUTH_ISSUER_URI:http://localhost:9000}
  cors:
    # Origins allowed to call the OAuth2 endpoints from a browser (the SPA).
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
  spa:
    # Redirect URIs registered for the SPA OAuth client (comma-separated).
    redirect-uris: ${SPA_REDIRECT_URIS:http://localhost:5173/callback}

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

Two patterns worth flagging:

- **Schema separation.** Both services share one Postgres database. auth-server
  owns the `auth` schema; resource-api will own `app`. `connection-init-sql`
  sets the search path on every Hikari connection, so the app's SQL doesn't
  have to qualify table names. Flyway creates the schema (`create-schemas:
  true`) and runs migrations into it (`default-schema: auth`).
- **`forward-headers-strategy: framework`** is essential when the service
  runs behind nginx — without it, OAuth redirects come back pointed at the
  wrong port. Part 5 has the matching nginx config that sends the right
  forwarded headers.

## Step 6 — User details service

Form login needs a way to look up users. Create
`auth-server/src/main/java/com/apipilot/authserver/user/JdbcUserDetailsService.java`:

```java
package com.apipilot.authserver.user;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users for form login from the {@code users} and {@code user_authorities}
 * tables.
 */
@Service
public class JdbcUserDetailsService implements UserDetailsService {

    private static final String SELECT_USER = """
            SELECT username, password_hash, enabled
            FROM users
            WHERE username = ?
            """;

    private static final String SELECT_AUTHORITIES = """
            SELECT a.authority
            FROM user_authorities a
            JOIN users u ON u.id = a.user_id
            WHERE u.username = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<UserRecord> users = jdbcTemplate.query(SELECT_USER,
                (rs, rowNum) -> new UserRecord(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getBoolean("enabled")),
                username);
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        UserRecord user = users.get(0);

        List<String> authorities = jdbcTemplate.queryForList(SELECT_AUTHORITIES, String.class, username);
        if (authorities.isEmpty()) {
            authorities = List.of("ROLE_USER");
        }

        return User.builder()
                .username(user.username())
                .password(user.passwordHash())
                .disabled(!user.enabled())
                .authorities(authorities.toArray(String[]::new))
                .build();
    }

    private record UserRecord(String username, String passwordHash, boolean enabled) {
    }
}
```

## Step 7 — The legacy client adapter

This is the headline piece of Part 3 and the reason the prototype is
interesting at all.

Create `auth-server/src/main/java/com/apipilot/authserver/client/LegacyClient.java`:

```java
package com.apipilot.authserver.client;

/**
 * A raw row from the legacy {@code oauth_client_details} table.
 *
 * <p>This mirrors the schema of the deprecated Spring Security OAuth2 project.
 * It is translated into a modern {@code RegisteredClient} by
 * {@link LegacyClientMapper}.
 */
public record LegacyClient(
        String clientId,
        String clientSecret,
        String scope,
        String authorizedGrantTypes,
        String webServerRedirectUri,
        Integer accessTokenValidity,
        Integer refreshTokenValidity,
        String autoApprove) {
}
```

Create `auth-server/src/main/java/com/apipilot/authserver/client/LegacyClientMapper.java`:

```java
package com.apipilot.authserver.client;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.StringUtils;

/**
 * Translates a legacy {@link LegacyClient} row into a modern
 * {@link RegisteredClient} understood by Spring Authorization Server.
 *
 * <p>The legacy schema does not map one-to-one onto the modern model:
 * <ul>
 *   <li>A client with no secret is treated as a public client (SPA): client
 *       authentication {@code NONE} and PKCE required.</li>
 *   <li>A client with a secret is confidential: {@code CLIENT_SECRET_BASIC}.</li>
 *   <li>{@code autoapprove = 'true'} skips the consent screen.</li>
 *   <li>Legacy grant types that the modern server does not support
 *       ({@code password}, {@code implicit}) are dropped.</li>
 * </ul>
 */
public final class LegacyClientMapper {

    private LegacyClientMapper() {
    }

    public static RegisteredClient toRegisteredClient(LegacyClient client) {
        boolean publicClient = !StringUtils.hasText(client.clientSecret());

        RegisteredClient.Builder builder = RegisteredClient.withId(client.clientId())
                .clientId(client.clientId())
                .clientName(client.clientId());

        if (publicClient) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            builder.clientSecret(client.clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        }

        splitCsv(client.authorizedGrantTypes()).forEach(grant -> addGrantType(builder, grant));
        splitCsv(client.webServerRedirectUri()).forEach(builder::redirectUri);
        splitCsv(client.scope()).forEach(builder::scope);

        boolean autoApprove = "true".equalsIgnoreCase(client.autoApprove());
        builder.clientSettings(ClientSettings.builder()
                .requireProofKey(publicClient)
                .requireAuthorizationConsent(!autoApprove)
                .build());

        TokenSettings.Builder tokenSettings = TokenSettings.builder();
        if (client.accessTokenValidity() != null && client.accessTokenValidity() > 0) {
            tokenSettings.accessTokenTimeToLive(Duration.ofSeconds(client.accessTokenValidity()));
        }
        if (client.refreshTokenValidity() != null && client.refreshTokenValidity() > 0) {
            tokenSettings.refreshTokenTimeToLive(Duration.ofSeconds(client.refreshTokenValidity()));
        }
        builder.tokenSettings(tokenSettings.build());

        return builder.build();
    }

    private static void addGrantType(RegisteredClient.Builder builder, String grant) {
        switch (grant) {
            case "authorization_code" ->
                    builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
            case "refresh_token" ->
                    builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
            case "client_credentials" ->
                    builder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
            default -> {
                // Legacy grant types such as "password" and "implicit" are
                // intentionally not carried over.
            }
        }
    }

    private static List<String> splitCsv(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
```

Create `auth-server/src/main/java/com/apipilot/authserver/client/LegacyClientDetailsRegisteredClientRepository.java`:

```java
package com.apipilot.authserver.client;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Repository;

/**
 * A {@link RegisteredClientRepository} backed by the legacy
 * {@code oauth_client_details} table.
 */
@Repository
public class LegacyClientDetailsRegisteredClientRepository implements RegisteredClientRepository {

    private static final String SELECT_BY_CLIENT_ID = """
            SELECT client_id, client_secret, scope, authorized_grant_types,
                   web_server_redirect_uri, access_token_validity,
                   refresh_token_validity, autoapprove
            FROM oauth_client_details
            WHERE client_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public LegacyClientDetailsRegisteredClientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException(
                "OAuth clients are managed directly in the legacy oauth_client_details table");
    }

    @Override
    public RegisteredClient findById(String id) {
        // The RegisteredClient id is the legacy client_id (see LegacyClientMapper).
        return findByClientId(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        List<RegisteredClient> matches = jdbcTemplate.query(SELECT_BY_CLIENT_ID, this::mapRow, clientId);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private RegisteredClient mapRow(ResultSet rs, int rowNum) throws SQLException {
        LegacyClient client = new LegacyClient(
                rs.getString("client_id"),
                rs.getString("client_secret"),
                rs.getString("scope"),
                rs.getString("authorized_grant_types"),
                rs.getString("web_server_redirect_uri"),
                getInteger(rs, "access_token_validity"),
                getInteger(rs, "refresh_token_validity"),
                rs.getString("autoapprove"));
        return LegacyClientMapper.toRegisteredClient(client);
    }

    private static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
```

Spring Authorization Server picks this `@Repository` up via type and uses it
instead of the in-memory default.

## Step 8 — Authorization Server config

Create `auth-server/src/main/java/com/apipilot/authserver/config/AuthorizationServerConfig.java`:

<details>
<summary>AuthorizationServerConfig.java — protocol filter chain, RSA key, in-memory authorization, token customizer</summary>

```java
package com.apipilot.authserver.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
public class AuthorizationServerConfig {

    /** Security filter chain for the OAuth2 / OIDC protocol endpoints. */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .cors(Customizer.withDefaults())
                .with(authorizationServerConfigurer, authorizationServer ->
                        authorizationServer.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService() {
        return new InMemoryOAuth2AuthorizationConsentService();
    }

    /** Copies the authenticated principal's authorities into access tokens. */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Authentication principal = context.getPrincipal();
                Set<String> authorities = principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                context.getClaims().claim("authorities", authorities);
            }
        };
    }

    /** RSA key used to sign JWTs; the public key is published at the JWKS endpoint. */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${app.issuer-uri}") String issuerUri) {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri)
                .build();
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate RSA signing key", ex);
        }
    }
}
```

</details>

Things worth knowing about this config:

- **Two filter chains**, ordered with `@Order(1)` and `@Order(2)` (Step 9).
  Chain 1 only matches OAuth/OIDC endpoints; everything else falls through
  to chain 2.
- **In-memory authorization service.** A single instance with stateless JWTs
  doesn't need a database-backed authorization store. Skipping it sidesteps
  the SAS JDBC schema entirely.
- **RSA key generated on startup** — fine for a single-instance prototype,
  not appropriate for production (every restart invalidates outstanding
  refresh tokens). For multi-instance, load a stable key from SSM or KMS.

## Step 9 — Web security and CORS

Create `auth-server/src/main/java/com/apipilot/authserver/config/WebSecurityConfig.java`:

```java
package com.apipilot.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for everything that is not an OAuth2 protocol endpoint: the custom
 * login page, actuator, and the API docs.
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login", "/error",
                                "/actuator/**",
                                "/css/**", "/img/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    /** Delegating encoder — verifies and creates {@code {bcrypt}} hashes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

Create `auth-server/src/main/java/com/apipilot/authserver/config/CorsConfig.java`:

```java
package com.apipilot.authserver.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS for the OAuth2 / OIDC endpoints. During local development the React SPA
 * runs on a different origin and calls the token, JWKS, and discovery
 * endpoints from the browser.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

## Step 10 — Login page

Create `auth-server/src/main/java/com/apipilot/authserver/web/LoginController.java`:

```java
package com.apipilot.authserver.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
```

Create `auth-server/src/main/resources/templates/login.html`:

<details>
<summary>login.html — branded login form with inline CSS</summary>

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Sign in — API Gateway Pilot</title>
  <style>
    :root { --indigo: #4f46e5; --indigo-dark: #312e81; --cyan: #06b6d4; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: linear-gradient(135deg, var(--indigo) 0%, var(--indigo-dark) 100%);
    }
    .card {
      width: 360px;
      max-width: 92vw;
      background: #fff;
      border-radius: 14px;
      padding: 2.5rem 2rem;
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
    }
    h1 { margin: 0; font-size: 1.4rem; color: var(--indigo-dark); }
    .sub { margin: 0.25rem 0 1.5rem; color: #6b7280; font-size: 0.95rem; }
    label { display: block; font-size: 0.85rem; font-weight: 600; color: #374151; margin-bottom: 0.3rem; }
    input {
      width: 100%;
      padding: 0.6rem 0.7rem;
      margin-bottom: 1rem;
      border: 1px solid #d1d5db;
      border-radius: 8px;
      font-size: 0.95rem;
    }
    input:focus { outline: 2px solid var(--indigo); border-color: var(--indigo); }
    button {
      width: 100%;
      padding: 0.7rem;
      border: 0;
      border-radius: 8px;
      background: var(--indigo);
      color: #fff;
      font-size: 0.95rem;
      font-weight: 600;
      cursor: pointer;
    }
    button:hover { background: #4338ca; }
    .alert { padding: 0.6rem 0.7rem; border-radius: 8px; font-size: 0.85rem; margin-bottom: 1rem; }
    .alert.error { background: #fef2f2; color: #b91c1c; }
    .alert.ok { background: #ecfdf5; color: #047857; }
    .hint { margin-top: 1.25rem; font-size: 0.8rem; color: #9ca3af; text-align: center; }
    code { background: #f3f4f6; padding: 0.05rem 0.3rem; border-radius: 4px; }
  </style>
</head>
<body>
  <main class="card">
    <h1>API Gateway Pilot</h1>
    <p class="sub">Sign in to continue</p>

    <div class="alert error" th:if="${param.error}">Invalid username or password.</div>
    <div class="alert ok" th:if="${param.logout}">You have been signed out.</div>

    <form th:action="@{/login}" method="post">
      <label for="username">Username</label>
      <input type="text" id="username" name="username" autocomplete="username" autofocus required>

      <label for="password">Password</label>
      <input type="password" id="password" name="password" autocomplete="current-password" required>

      <button type="submit">Sign in</button>
    </form>

    <p class="hint">Demo users <code>alice</code> / <code>bob</code> — password <code>password</code></p>
  </main>
</body>
</html>
```

</details>

`th:action="@{/login}"` plus Thymeleaf's Spring Security integration adds the
CSRF token automatically; you don't have to think about it.

## Step 11 — Seed demo data

Create `auth-server/src/main/java/com/apipilot/authserver/init/DemoDataInitializer.java`:

<details>
<summary>DemoDataInitializer.java — seeds OAuth clients and users on first boot</summary>

```java
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
 * {@link PasswordEncoder} the server uses to verify them.
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
```

</details>

Seeding lives in application code — not a Flyway migration — because the
client secrets and user passwords are bcrypt-encoded using the same
`PasswordEncoder` bean the server uses to verify them. After every
`terraform destroy` / `apply` you get a fresh, immediately-usable DB.

## Step 12 — OpenAPI metadata

Create `auth-server/src/main/java/com/apipilot/authserver/config/OpenApiConfig.java`:

```java
package com.apipilot.authserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("auth-server API")
                .version("0.1.0")
                .description("OAuth2 Authorization Server. The OAuth2 / OIDC protocol "
                        + "endpoints are provided by Spring Authorization Server under "
                        + "/oauth2/** and /.well-known/**."));
    }
}
```

## Step 13 — Dockerfile

Create `auth-server/Dockerfile`:

```dockerfile
# syntax=docker/dockerfile:1
# Build context is the repository root (the module inherits the parent POM).

# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
# Copy every module's POM so the Maven reactor can read the parent's
# <modules>; only auth-server's sources are copied, so only it gets built.
COPY auth-server/pom.xml auth-server/pom.xml
COPY resource-api/pom.xml resource-api/pom.xml
COPY auth-server/src auth-server/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl auth-server -am -DskipTests clean package

# --- runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system spring \
 && useradd --system --gid spring spring
USER spring
COPY --from=build /workspace/auth-server/target/auth-server.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

:::caution Multi-module Maven gotcha
The reactor reads the parent POM, which lists `auth-server` **and**
`resource-api`. Even with `mvn -pl auth-server -am`, every listed module's
`pom.xml` has to be readable, or Maven refuses to start with
`Child module /workspace/resource-api of /workspace/pom.xml does not exist`.

That's why the Dockerfile copies **both** module POMs but only auth-server's
`src/`. `resource-api/pom.xml` doesn't exist yet — create an empty placeholder
for it now (Part 4 fills it in):

```sh
mkdir -p resource-api && touch resource-api/pom.xml
```

Or copy the Part 4 file content first and double back to Part 3 — your call.
:::

Actually the placeholder is awkward. The cleaner approach: **don't add
`<module>resource-api</module>` to the parent POM yet** — add it in Part 4.
For now the parent `<modules>` block contains only `<module>auth-server</module>`,
and the Dockerfile drops the `resource-api/pom.xml` COPY line:

```dockerfile
COPY pom.xml ./
COPY auth-server/pom.xml auth-server/pom.xml
COPY auth-server/src auth-server/src
```

Part 4 will add the resource-api module to the parent POM and add the second
`COPY` line back to `auth-server/Dockerfile`.

## Step 14 — Wire into docker-compose

Replace `docker-compose.yml` at the repo root with:

```yaml
# Local development stack.
# resource-api and nginx are added in later parts.
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
      # Default issuer for Parts 3-4 (before nginx). Part 5 sets it to the
      # nginx-fronted URL once the SPA needs it.
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

volumes:
  pgdata:
```

## Step 15 — Tests

Two tests, both optional but recommended.

The **mapper unit test** runs without Docker. Create
`auth-server/src/test/java/com/apipilot/authserver/client/LegacyClientMapperTest.java`:

<details>
<summary>LegacyClientMapperTest.java — verifies the public/confidential split, the consent flag, and dropped grant types</summary>

```java
package com.apipilot.authserver.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class LegacyClientMapperTest {

    @Test
    void mapsPublicClientWithPkceAndNoConsent() {
        LegacyClient legacy = new LegacyClient(
                "spa-client", null, "openid,profile,read",
                "authorization_code,refresh_token",
                "http://localhost:5173/callback", 900, 86400, "true");

        RegisteredClient client = LegacyClientMapper.toRegisteredClient(legacy);

        assertThat(client.getId()).isEqualTo("spa-client");
        assertThat(client.getClientId()).isEqualTo("spa-client");
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(client.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE,
                AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(client.getScopes()).containsExactlyInAnyOrder("openid", "profile", "read");
        assertThat(client.getRedirectUris()).containsExactly("http://localhost:5173/callback");
        assertThat(client.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    void mapsConfidentialClientWithSecret() {
        LegacyClient legacy = new LegacyClient(
                "resource-api", "{noop}secret", "internal.read",
                "client_credentials", null, 900, null, "false");

        RegisteredClient client = LegacyClientMapper.toRegisteredClient(legacy);

        assertThat(client.getClientSecret()).isEqualTo("{noop}secret");
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(client.getClientSettings().isRequireProofKey()).isFalse();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isTrue();
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
    }

    @Test
    void dropsUnsupportedLegacyGrantTypes() {
        LegacyClient legacy = new LegacyClient(
                "legacy-app", "{noop}s", "read",
                "password,authorization_code,implicit",
                "http://localhost/callback", null, null, null);

        RegisteredClient client = LegacyClientMapper.toRegisteredClient(legacy);

        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
    }
}
```

</details>

The **context-load test** boots the full Spring app against a Postgres
container — needs Docker. Create
`auth-server/src/test/java/com/apipilot/authserver/AuthServerApplicationTests.java`:

```java
package com.apipilot.authserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
```

Run the tests:

```sh
mvn -pl auth-server test
```

You should see four passing tests (three from the mapper + the
`contextLoads` integration).

## Verify the running service

```sh
make up
make ps
```

After ~30–60s `agp-auth-server` should be `(healthy)`. Then:

```sh
curl -s http://localhost:9000/.well-known/openid-configuration | head -20
```

You should get a JSON document listing the OAuth2 / OIDC endpoints under
`http://localhost:9000`.

Open the login page in a browser: **http://localhost:9000/login**. Sign in
with `alice` / `password`. You'll get redirected to `/error` because there's
no client redirecting through yet — that's expected. The login itself
worked.

Get a token via the service client credentials:

```sh
curl -s -u resource-api:resource-api-secret \
  -d 'grant_type=client_credentials&scope=internal.read' \
  http://localhost:9000/oauth2/token
```

You should get a JSON response with `access_token`, `token_type: Bearer`,
and `expires_in: 900`. Decode the JWT at [jwt.io](https://jwt.io) — the
`iss` claim is `http://localhost:9000` and the signature verifies against
the JWKS at `/oauth2/jwks`.

## Update local-development.md

In `docs/docs/local-development.md`, update the **What runs today** table:

```md
| Part | Added to the local stack |
| --- | --- |
| 1 | PostgreSQL |
| 3 | `auth-server` |
| 4 | `resource-api` |
| 5 | nginx reverse proxy |

:::note
Today `make up` starts PostgreSQL and the auth-server.
:::
```

## Commit

```sh
git add -A
git commit -m "feat: oauth2 authorization server with legacy client repository"
```

## What's next

**Part 4 — resource-api.** A Spring Boot resource server that validates the
tokens auth-server issues and serves user and device data. You'll see how
the JWT validation works end-to-end and how each service owns its own
database schema.
