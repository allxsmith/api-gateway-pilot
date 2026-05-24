package com.apipilot.resourceapi.device;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import com.apipilot.resourceapi.user.UserInfoRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads and records rows in the {@code device_info} table. */
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

    /**
     * Records the device for the user. A device with the same name is updated
     * in place (its last-seen timestamp is refreshed) rather than duplicated.
     */
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
