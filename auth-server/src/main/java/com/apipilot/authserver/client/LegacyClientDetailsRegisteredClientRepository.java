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
 *
 * <p>This is the bridge that lets the modern Spring Authorization Server keep
 * using the original OAuth client registry. Clients are read-only here — they
 * are managed directly in the table (seeded by {@code DemoDataInitializer}).
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
