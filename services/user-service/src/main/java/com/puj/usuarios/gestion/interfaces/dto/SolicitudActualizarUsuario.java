package com.puj.usuarios.gestion.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * DTO de solicitud para actualizar los datos de un usuario existente.
 *
 * <p>Todos los campos son opcionales; los campos nulos o en blanco son ignorados
 * por la capa de servicio y no producen cambios en el registro. La contraseña
 * se actualiza sólo cuando {@code nuevaContrasena} tiene un valor no vacío.
 *
 * @param nombre          nuevo nombre de pila; máximo 100 caracteres.
 * @param apellido        nuevo apellido; máximo 100 caracteres.
 * @param nuevaContrasena nueva contraseña en texto plano; mínimo 8 caracteres.
 * @author Plataforma PUJ
 * @since 1.0
 */
public record SolicitudActualizarUsuario(
        @Size(max = 100)
        @JsonProperty("firstName")
        String nombre,

        @Size(max = 100)
        @JsonProperty("lastName")
        String apellido,

        @Size(min = 8, max = 100)
        @JsonProperty("newPassword")
        String nuevaContrasena
) {}
