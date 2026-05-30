package com.puj.assessments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada para la creación de una evaluación con sus preguntas y opciones.
 *
 * <p>Los campos {@code title} y {@code courseId} son obligatorios.
 * Las preguntas y sus opciones se validan de forma transitiva mediante los
 * records anidados {@link QuestionRequest} y {@link OptionRequest}.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record AssessmentCreateRequest(
        /** Título visible de la evaluación. No puede estar en blanco. */
        @NotBlank String title,

        /** Identificador del curso al que pertenece la evaluación. */
        @NotNull UUID courseId,

        /** Identificador de la lección asociada (opcional). */
        UUID lessonId,

        /** Descripción o instrucciones adicionales para el estudiante. */
        String description,

        /** Porcentaje mínimo de aciertos para aprobar (0-100). Por defecto 60. */
        Double passingScorePct,

        /** Número máximo de intentos permitidos. Por defecto 3. */
        Integer maxAttempts,

        /** Lista de preguntas a crear junto con la evaluación. */
        List<QuestionRequest> questions
) {

    /**
     * DTO para una pregunta dentro de la solicitud de creación.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record QuestionRequest(
            /** Enunciado de la pregunta. No puede estar en blanco. */
            @NotBlank String text,

            /**
             * Tipo de pregunta: {@code SINGLE_CHOICE}, {@code MULTIPLE_CHOICE},
             * {@code TRUE_FALSE} o {@code SHORT_ANSWER}.
             */
            @NotBlank String type,

            /** Puntos asignados a esta pregunta. Debe ser mayor que 0. */
            double points,

            /** Opciones de respuesta asociadas a la pregunta. */
            List<OptionRequest> options
    ) {}

    /**
     * DTO para una opción de respuesta dentro de una pregunta.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record OptionRequest(
            /** Texto visible de la opción de respuesta. No puede estar en blanco. */
            @NotBlank String text,

            /** Indica si esta opción es la respuesta correcta. */
            boolean correct
    ) {}
}
