package com.puj.assessments.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * DTO de entrada para el envío de respuestas de una evaluación.
 *
 * <p>El mapa {@code answers} relaciona el UUID de cada pregunta (como {@code String})
 * con la lista de IDs de las opciones seleccionadas. Para preguntas de opción única
 * o verdadero/falso la lista contiene exactamente un elemento; para selección
 * múltiple puede contener varios.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record SubmitRequest(
        /**
         * Respuestas del estudiante indexadas por ID de pregunta.
         * Clave: UUID de la pregunta como {@code String}.
         * Valor: lista de UUIDs de las opciones seleccionadas como {@code String}.
         */
        @NotNull Map<String, List<String>> answers,

        /** Duración en segundos que el estudiante empleó para completar la evaluación. */
        long durationSeconds
) {}
