package com.apipilot.resourceapi.user;

import org.springframework.security.oauth2.jwt.Jwt;

/** A user's profile, as returned by {@code GET /api/me}. */
public record UserInfo(
        String username,
        String fullName,
        String email,
        String phone,
        String department,
        String jobTitle) {

    /**
     * Builds a minimal profile from the access token, for an authenticated user
     * who has no {@code user_info} row yet.
     */
    public static UserInfo fromJwt(Jwt jwt) {
        return new UserInfo(jwt.getSubject(), null, null, null, null, null);
    }
}
