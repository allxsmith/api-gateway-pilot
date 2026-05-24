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
