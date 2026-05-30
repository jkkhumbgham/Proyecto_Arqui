package com.puj.evaluaciones.evaluaciones.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada para la creación de una evaluación con sus preguntas y opciones.
 *
 * <p>Los campos {@code title} y {@code courseId} son obligatorios.
 * Las preguntas y sus opciones se validan de forma transitiva mediante los
 * records anidados {@link SolicitudPregunta} y {@link SolicitudOpcion}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record SolicitudCrearEvaluacion(
        /** Título visible de la evaluación. No puede estar en blanco. */
        @NotBlank
        @JsonProperty("title")
        String titulo,

        /** Identificador del curso al que pertenece la evaluación. */
        @NotNull
        @JsonProperty("courseId")
        UUID idCurso,

        /** Identificador de la lección asociada (opcional). */
        @JsonProperty("lessonId")
        UUID idLeccion,

        /** Descripción o instrucciones adicionales para el estudiante. */
        @JsonProperty("description")
        String descripcion,

        /** Porcentaje mínimo de aciertos para aprobar (0-100). Por defecto 60. */
        @JsonProperty("passingScorePct")
        Double porcentajeAprobacion,

        /** Número máximo de intentos permitidos. Por defecto 3. */
        @JsonProperty("maxAttempts")
        Integer maxIntentos,

        /** Lista de preguntas a crear junto con la evaluación. */
        @JsonProperty("questions")
        List<SolicitudPregunta> preguntas
) {

    /**
     * DTO para una pregunta dentro de la solicitud de creación.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record SolicitudPregunta(
            /** Enunciado de la pregunta. No puede estar en blanco. */
            @NotBlank
            @JsonProperty("text")
            String texto,

            /**
             * Tipo de pregunta: {@code SINGLE_CHOICE}, {@code MULTIPLE_CHOICE},
             * {@code TRUE_FALSE} o {@code SHORT_ANSWER}.
             */
            @NotBlank
            @JsonProperty("type")
            String tipo,

            /** Puntos asignados a esta pregunta. Debe ser mayor que 0. */
            @JsonProperty("points")
            double puntos,

            /** Opciones de respuesta asociadas a la pregunta. */
            @JsonProperty("options")
            List<SolicitudOpcion> opciones
    ) {}

    /**
     * DTO para una opción de respuesta dentro de una pregunta.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record SolicitudOpcion(
            /** Texto visible de la opción de respuesta. No puede estar en blanco. */
            @NotBlank
            @JsonProperty("text")
            String texto,

            /** Indica si esta opción es la respuesta correcta. */
            @JsonProperty("correct")
            boolean correcta
    ) {}
}
