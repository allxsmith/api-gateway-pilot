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
