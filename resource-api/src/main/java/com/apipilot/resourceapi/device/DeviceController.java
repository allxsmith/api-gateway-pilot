package com.apipilot.resourceapi.device;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the authenticated user's devices. */
@RestController
@RequestMapping("/api")
@Tag(name = "Devices", description = "Devices the user has signed in from")
public class DeviceController {

    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @GetMapping("/devices")
    @Operation(summary = "List the current user's devices")
    public List<DeviceInfo> devices(@AuthenticationPrincipal Jwt jwt) {
        return deviceRepository.findByUsername(jwt.getSubject());
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record the device the user signed in from")
    public DeviceInfo register(@AuthenticationPrincipal Jwt jwt,
                               @Valid @RequestBody RegisterDeviceRequest request) {
        return deviceRepository.recordDevice(jwt.getSubject(), request);
    }
}
