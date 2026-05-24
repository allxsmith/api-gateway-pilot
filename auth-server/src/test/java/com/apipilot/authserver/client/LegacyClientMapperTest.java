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

        // "password" and "implicit" are not supported by the modern server.
        assertThat(client.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
    }
}
