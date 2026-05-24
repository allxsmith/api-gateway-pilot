package com.apipilot.resourceapi.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/devices}. The SPA sends this after login to record
 * the device the user signed in from.
 */
public record RegisterDeviceRequest(
        @NotBlank @Size(max = 150) String deviceName,
        @Size(max = 50) String deviceType,
        @Size(max = 80) String os,
        @Size(max = 80) String browser) {
}
