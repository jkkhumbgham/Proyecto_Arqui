package com.puj.usuarios.autenticacion.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.seguridad.rbac.Rol;
import com.puj.usuarios.autenticacion.dominio.Usuario;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta con los datos públicos de un usuario.
 *
 * <p>Este record omite información sensible como el hash de la contraseña y los
 * contadores de intentos fallidos. Se utiliza como respuesta estándar en todos
 * los endpoints de usuario y autenticación.
 *
 * @param id          identificador único del usuario.
 * @param email       dirección de correo electrónico.
 * @param nombre      nombre de pila.
 * @param apellido    apellido.
 * @param rol         rol RBAC asignado.
 * @param activo      indica si la cuenta está activa.
 * @param creadoEn    instante de creación de la cuenta.
 * @param ultimoAcceso instante del último inicio de sesión exitoso.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record RespuestaUsuario(
        UUID    id,
        String  email,

        @JsonProperty("firstName")
        String  nombre,

        @JsonProperty("lastName")
        String  apellido,

        @JsonProperty("role")
        Rol     rol,

        @JsonProperty("active")
        boolean activo,

        @JsonProperty("createdAt")
        Instant creadoEn,

        @JsonProperty("lastLoginAt")
        Instant ultimoAcceso
) {
    /**
     * Construye un {@code RespuestaUsuario} a partir de una entidad {@link Usuario}.
     *
     * @param usuario entidad de usuario de la que se extraen los datos.
     * @return instancia de {@code RespuestaUsuario} con los datos públicos.
     */
    public static RespuestaUsuario desde(Usuario usuario) {
        return new RespuestaUsuario(
                usuario.getId(),
                usuario.obtenerCorreo(),
                usuario.obtenerNombre(),
                usuario.obtenerApellido(),
                usuario.obtenerRol(),
                usuario.esActivo(),
                usuario.obtenerCreadoEn(),
                usuario.obtenerUltimoAcceso()
        );
    }
}
