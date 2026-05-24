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
 * <p>The legacy schema does not map one-to-one onto the modern model, so a few
 * decisions are made here:
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

        // The legacy client_id doubles as the RegisteredClient id.
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
                // intentionally not carried over to the modern server.
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
