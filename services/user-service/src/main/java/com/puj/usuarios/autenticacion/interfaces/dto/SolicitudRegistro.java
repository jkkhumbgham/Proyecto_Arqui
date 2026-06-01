package com.puj.usuarios.autenticacion.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

/**
 * DTO de solicitud para el registro de un nuevo usuario en la plataforma.
 *
 * <p>Todos los campos son obligatorios. El campo {@code consentimientoDado} debe ser
 * {@code true} para que el registro sea aceptado conforme a la Ley 1581/2012
 * de protección de datos personales de Colombia.
 *
 * @param email              dirección de correo electrónico única del usuario.
 * @param contrasena         contraseña en texto plano; mínimo 8 caracteres.
 * @param nombre             nombre de pila; máximo 100 caracteres.
 * @param apellido           apellido; máximo 100 caracteres.
 * @param consentimientoDado indicador de consentimiento de tratamiento de datos
 *                           personales; debe ser {@code true} para registrarse.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record SolicitudRegistro(
        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 100)
        @JsonProperty("password")
        String contrasena,

        @NotBlank @Size(max = 100)
        @JsonProperty("firstName")
        String nombre,

        @NotBlank @Size(max = 100)
        @JsonProperty("lastName")
        String apellido,

        @NotNull
        @JsonProperty("consentGiven")
        Boolean consentimientoDado
) {}
