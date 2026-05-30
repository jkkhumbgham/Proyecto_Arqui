package com.puj.users.dto;

import com.puj.security.rbac.Role;
import com.puj.users.entity.User;

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
 * @param firstName   nombre de pila.
 * @param lastName    apellido.
 * @param role        rol RBAC asignado.
 * @param active      indica si la cuenta está activa.
 * @param createdAt   instante de creación de la cuenta.
 * @param lastLoginAt instante del último inicio de sesión exitoso.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record UserResponse(
        UUID    id,
        String  email,
        String  firstName,
        String  lastName,
        Role    role,
        boolean active,
        Instant createdAt,
        Instant lastLoginAt
) {
    /**
     * Construye un {@code UserResponse} a partir de una entidad {@link User}.
     *
     * @param user entidad de usuario de la que se extraen los datos.
     * @return instancia de {@code UserResponse} con los datos públicos del usuario.
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
