package com.apipilot.resourceapi.device;

import java.time.Instant;

/** A device belonging to a user, as returned by {@code GET /api/devices}. */
public record DeviceInfo(
        String deviceName,
        String deviceType,
        String os,
        String browser,
        Instant lastSeenAt,
        Instant registeredAt) {
}
