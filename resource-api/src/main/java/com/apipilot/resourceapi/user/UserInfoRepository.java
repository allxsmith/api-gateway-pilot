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

    /**
     * Returns the id of the {@code user_info} row for the username, creating a
     * minimal row if none exists. Used when a device is registered for a user
     * the resource server has not seen before.
     */
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
