package com.puj.security.jwt;

import java.time.Instant;

/**
 * Representa los claims extraídos de un JWT válido.
 *
 * <p>Este record es inmutable y se produce únicamente por {@link JwtProvider#validateToken(String)}
 * tras validar la firma y la vigencia del token. Todos los campos están garantizados
 * como no nulos cuando se obtiene una instancia desde ese método.</p>
 *
 * @param jti       identificador único del token (JWT ID); se usa para la blacklist en Redis
 * @param userId    identificador único del usuario autenticado (UUID como String)
 * @param email     correo institucional del usuario
 * @param role      nombre del rol asignado al usuario (ej. {@code "STUDENT"}, {@code "ADMIN"})
 * @param issuedAt  instante en que fue emitido el token
 * @param expiresAt instante en que expira el token
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see JwtProvider
 */
public record JwtClaims(
        String  jti,
        String  userId,
        String  email,
        String  role,
        Instant issuedAt,
        Instant expiresAt
) {}
