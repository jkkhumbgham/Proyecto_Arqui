package com.puj.users.dto;

import jakarta.validation.constraints.*;

/**
 * DTO de solicitud para el registro de un nuevo usuario en la plataforma.
 *
 * <p>Todos los campos son obligatorios. El campo {@code consentGiven} debe ser
 * {@code true} para que el registro sea aceptado conforme a la Ley 1581/2012
 * de protección de datos personales de Colombia.
 *
 * @param email        dirección de correo electrónico única del usuario.
 * @param password     contraseña en texto plano; mínimo 8 caracteres.
 * @param firstName    nombre de pila; máximo 100 caracteres.
 * @param lastName     apellido; máximo 100 caracteres.
 * @param consentGiven indicador de consentimiento de tratamiento de datos
 *                     personales; debe ser {@code true} para registrarse.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 100)
        String password,

        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @NotNull
        Boolean consentGiven
) {}
