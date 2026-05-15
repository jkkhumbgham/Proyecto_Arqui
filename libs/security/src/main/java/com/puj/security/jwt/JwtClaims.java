package com.puj.security.jwt;

import java.time.Instant;

public record JwtClaims(
        String jti,
        String userId,
        String email,
        String role,
        Instant issuedAt,
        Instant expiresAt
) {}
