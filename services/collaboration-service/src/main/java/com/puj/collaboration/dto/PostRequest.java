package com.puj.collaboration.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de entrada para crear una respuesta (post) en un hilo del foro.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record PostRequest(
        /** Contenido de la respuesta. No puede estar en blanco. */
        @NotBlank String content
) {}
