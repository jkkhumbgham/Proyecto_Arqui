package com.puj.colaboracion.foros.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de entrada para crear una respuesta (publicación) en un hilo del foro.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record SolicitudPublicacion(
        /** Contenido de la respuesta. No puede estar en blanco. */
        @JsonProperty("content")
        @NotBlank String contenido
) {}
