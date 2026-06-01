package com.puj.usuarios.gestion.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de solicitud para la creación de un usuario por parte de un administrador.
 *
 * <p>A diferencia del registro público, este endpoint permite asignar un rol
 * arbitrario al usuario creado. El campo {@code rol} acepta los valores del
 * enum {@code Rol} en mayúsculas o minúsculas; si se omite se asigna
 * {@code STUDENT} por defecto.
 *
 * @param email              dirección de correo electrónico única del nuevo usuario.
 * @param contrasena         contraseña en texto plano; se almacenará como hash BCrypt.
 * @param nombre             nombre de pila del usuario.
 * @param apellido           apellido del usuario.
 * @param rol                nombre del rol a asignar; {@code null} equivale a {@code "STUDENT"}.
 * @param consentimientoDado indica si el consentimiento de datos fue recabado
 *                           previamente por el administrador.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record SolicitudCrearUsuario(
        @NotBlank @Email
        String email,

        @NotBlank
        @JsonProperty("password")
        String contrasena,

        @NotBlank
        @JsonProperty("firstName")
        String nombre,

        @NotBlank
        @JsonProperty("lastName")
        String apellido,

        @JsonProperty("role")
        String rol,

        @JsonProperty("consentGiven")
        boolean consentimientoDado
) {}
