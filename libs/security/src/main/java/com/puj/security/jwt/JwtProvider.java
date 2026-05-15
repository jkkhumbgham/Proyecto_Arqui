package com.puj.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class JwtProvider {

    private static final String CLAIM_ROLE  = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String ISSUER      = "puj-learning-platform";

    @Inject
    private KeyProvider keyProvider;

    public String generateAccessToken(String userId, String email, String role, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(userId)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(keyProvider.getPrivateKey())
                .compact();
    }

    public JwtClaims validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keyProvider.getPublicKey())
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new JwtClaims(
                    claims.getId(),
                    claims.getSubject(),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_ROLE, String.class),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException e) {
            throw new TokenValidationException("TOKEN_EXPIRED", "El token ha expirado");
        } catch (SignatureException e) {
            throw new TokenValidationException("INVALID_SIGNATURE", "Firma del token inválida");
        } catch (MalformedJwtException | IllegalArgumentException e) {
            throw new TokenValidationException("MALFORMED_TOKEN", "Token malformado");
        }
    }

    public long getRemainingTtlSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keyProvider.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Math.max(0, remaining / 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    public static class TokenValidationException extends RuntimeException {
        private final String code;

        public TokenValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }
}
