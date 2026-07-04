package com.cabin.login.service;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(
        UUID userId,
        String username,
        String roleCode,
        Instant expiresAt
) {
}
