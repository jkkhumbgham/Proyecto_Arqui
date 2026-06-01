package com.puj.colaboracion.foros.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para crear un hilo de discusión en un foro.
 *
 * <p>El primer post del hilo se crea automáticamente con el campo
 * {@code contenido} proporcionado en esta solicitud.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record SolicitudHilo(
        /** Título del hilo. No puede estar en blanco; máximo 300 caracteres. */
        @JsonProperty("title")
        @NotBlank @Size(max = 300) String titulo,

        /** Contenido de la primera publicación que abre la discusión. No puede estar en blanco. */
        @JsonProperty("content")
        @NotBlank String contenido
) {}
