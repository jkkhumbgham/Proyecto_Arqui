package com.puj.users.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO de solicitud para actualizar los datos de un usuario existente.
 *
 * <p>Todos los campos son opcionales; los campos nulos o en blanco son ignorados
 * por la capa de servicio y no producen cambios en el registro. La contraseña
 * se actualiza sólo cuando {@code newPassword} tiene un valor no vacío.
 *
 * @param firstName   nuevo nombre de pila; máximo 100 caracteres.
 * @param lastName    nuevo apellido; máximo 100 caracteres.
 * @param newPassword nueva contraseña en texto plano; mínimo 8 caracteres.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record UpdateUserRequest(
        @Size(max = 100)           String firstName,
        @Size(max = 100)           String lastName,
        @Size(min = 8, max = 100)  String newPassword
) {}
