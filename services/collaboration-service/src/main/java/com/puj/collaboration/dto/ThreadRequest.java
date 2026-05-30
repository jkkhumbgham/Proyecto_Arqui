package com.puj.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para crear un hilo de discusión en un foro.
 *
 * <p>El primer post del hilo se crea automáticamente con el campo
 * {@code content} proporcionado en este request.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record ThreadRequest(
        /** Título del hilo. No puede estar en blanco; máximo 300 caracteres. */
        @NotBlank @Size(max = 300) String title,

        /** Contenido del primer post que abre la discusión. No puede estar en blanco. */
        @NotBlank String content
) {}
