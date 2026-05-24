package com.apipilot.resourceapi.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the authenticated user's profile. */
@RestController
@RequestMapping("/api")
@Tag(name = "User", description = "The authenticated user's profile")
public class UserController {

    private final UserInfoRepository userInfoRepository;

    public UserController(UserInfoRepository userInfoRepository) {
        this.userInfoRepository = userInfoRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's profile")
    public UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        return userInfoRepository.findByUsername(username)
                .orElseGet(() -> UserInfo.fromJwt(jwt));
    }
}
