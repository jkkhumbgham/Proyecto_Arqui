package com.puj.seguridad.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Servicio de generación y validación de JSON Web Tokens (JWT) con algoritmo RS256.
 *
 * <p>Utiliza claves RSA-2048 provistas por {@link ProveedorClaves}. Los tokens emitidos
 * incluyen los claims estándar ({@code iss}, {@code sub}, {@code jti}, {@code iat},
 * {@code exp}) más claims de aplicación ({@code email}, {@code role}).</p>
 *
 * <p>La validación verifica firma, issuer y expiración. Los tokens revocados se
 * detectan por separado a través del {@code jti} consultado en Redis
 * ({@link com.puj.seguridad.listanegra.ServicioListaNegra}).</p>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see ProveedorClaves
 * @see ReclamosJwt
 */
@ApplicationScoped
public class ProveedorJwt {

    private static final String CLAIM_ROLE  = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String ISSUER      = "puj-learning-platform";

    @Inject
    private ProveedorClaves proveedorClaves;

    /**
     * Genera un access token JWT firmado con RS256.
     *
     * <p>El token incluye un {@code jti} único (UUID v4) para permitir su
     * revocación individual a través de la lista negra Redis.</p>
     *
     * @param idUsuario identificador único del usuario (UUID como String)
     * @param correo    correo institucional del usuario
     * @param rol       nombre del rol asignado (ej. {@code "STUDENT"})
     * @param ttl       duración de validez del token (ej. {@code Duration.ofHours(8)})
     * @return token JWT compacto firmado
     */
    public String generarTokenAcceso(String idUsuario, String correo, String rol, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(idUsuario)
                .claim(CLAIM_EMAIL, correo)
                .claim(CLAIM_ROLE, rol)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(proveedorClaves.obtenerClavePrivada())
                .compact();
    }

    /**
     * Valida un token JWT y extrae sus claims.
     *
     * <p>Verifica la firma RS256, el issuer y la expiración. No verifica si el
     * token está en la lista negra Redis (eso lo hace {@link FiltroJwt}).</p>
     *
     * @param token token JWT compacto a validar
     * @return claims extraídos del token
     * @throws ExcepcionValidacionToken si el token está expirado, tiene firma inválida o está malformado
     */
    public ReclamosJwt validarToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(proveedorClaves.obtenerClavePublica())
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new ReclamosJwt(
                    claims.getId(),
                    claims.getSubject(),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_ROLE, String.class),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException e) {
            throw new ExcepcionValidacionToken("TOKEN_EXPIRED", "El token ha expirado");
        } catch (SignatureException e) {
            throw new ExcepcionValidacionToken("INVALID_SIGNATURE", "Firma del token inválida");
        } catch (MalformedJwtException | IllegalArgumentException e) {
            throw new ExcepcionValidacionToken("MALFORMED_TOKEN", "Token malformado");
        }
    }

    /**
     * Calcula los segundos restantes de validez de un token sin lanzar excepción si expiró.
     *
     * <p>Se usa al revocar tokens (logout) para determinar el TTL de la entrada
     * en la lista negra Redis, de modo que la entrada expira automáticamente cuando
     * el token hubiera expirado de todas formas.</p>
     *
     * @param token token JWT compacto; puede estar expirado
     * @return segundos restantes hasta la expiración; {@code 0} si ya expiró o es inválido
     */
    public long obtenerTtlRestanteSegundos(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(proveedorClaves.obtenerClavePublica())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long restante = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Math.max(0, restante / 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Excepción lanzada cuando la validación de un token JWT falla.
     *
     * <p>Encapsula un código de error legible por máquina ({@link #obtenerCodigo()})
     * además del mensaje humano, para facilitar respuestas HTTP estructuradas.</p>
     */
    public static class ExcepcionValidacionToken extends RuntimeException {

        private final String codigo;

        /**
         * Crea una excepción de validación con código y mensaje.
         *
         * @param codigo  código de error legible por máquina (ej. {@code "TOKEN_EXPIRED"})
         * @param mensaje descripción legible por humanos
         */
        public ExcepcionValidacionToken(String codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }

        /**
         * Retorna el código de error estructurado para incluir en la respuesta HTTP.
         *
         * @return código de error (ej. {@code "TOKEN_EXPIRED"}, {@code "INVALID_SIGNATURE"})
         */
        public String obtenerCodigo() { return codigo; }
    }
}
