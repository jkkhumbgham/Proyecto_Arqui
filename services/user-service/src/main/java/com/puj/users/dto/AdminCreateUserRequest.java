package com.puj.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de solicitud para la creación de un usuario por parte de un administrador.
 *
 * <p>A diferencia del registro público, este endpoint permite asignar un rol
 * arbitrario al usuario creado. El campo {@code role} acepta los valores del
 * enum {@code Role} en mayúsculas o minúsculas; si se omite se asigna
 * {@code STUDENT} por defecto.
 *
 * @param email        dirección de correo electrónico única del nuevo usuario.
 * @param password     contraseña en texto plano; se almacenará como hash BCrypt.
 * @param firstName    nombre de pila del usuario.
 * @param lastName     apellido del usuario.
 * @param role         nombre del rol a asignar; {@code null} equivale a
 *                     {@code "STUDENT"}.
 * @param consentGiven indica si el consentimiento de datos fue recabado
 *                     previamente por el administrador.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record AdminCreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank        String password,
        @NotBlank        String firstName,
        @NotBlank        String lastName,
                         String  role,
                         boolean consentGiven
) {}
