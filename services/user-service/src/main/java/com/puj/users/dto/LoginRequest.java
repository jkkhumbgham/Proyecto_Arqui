package com.puj.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de solicitud para el inicio de sesión de un usuario.
 *
 * <p>Transporta las credenciales proporcionadas por el cliente hacia la capa
 * de autenticación. Ambos campos son obligatorios y el correo debe tener
 * formato válido según la especificación de Bean Validation.
 *
 * @param email    dirección de correo electrónico del usuario.
 * @param password contraseña en texto plano; se verifica contra el hash BCrypt.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank        String password
) {}
