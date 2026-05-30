package com.puj.seguridad.jwt;

import java.time.Instant;

/**
 * Representa los claims extraídos de un JWT válido.
 *
 * <p>Este record es inmutable y se produce únicamente por {@link ProveedorJwt#validarToken(String)}
 * tras validar la firma y la vigencia del token. Todos los campos están garantizados
 * como no nulos cuando se obtiene una instancia desde ese método.</p>
 *
 * @param jti        identificador único del token (JWT ID); se usa para la lista negra en Redis
 * @param idUsuario  identificador único del usuario autenticado (UUID como String)
 * @param correo     correo institucional del usuario
 * @param rol        nombre del rol asignado al usuario (ej. {@code "STUDENT"}, {@code "ADMIN"})
 * @param emitidoEn  instante en que fue emitido el token
 * @param expiraEn   instante en que expira el token
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see ProveedorJwt
 */
public record ReclamosJwt(
        String  jti,
        String  idUsuario,
        String  correo,
        String  rol,
        Instant emitidoEn,
        Instant expiraEn
) {}
