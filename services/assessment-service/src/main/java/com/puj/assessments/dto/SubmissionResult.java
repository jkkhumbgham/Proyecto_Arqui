package com.puj.assessments.dto;

import com.puj.assessments.adaptive.AdaptiveRecommendation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado devuelto al estudiante tras calificar una submission.
 *
 * <p>Incluye el puntaje obtenido, el máximo posible, si aprobó y,
 * opcionalmente, una recomendación adaptativa cuando el puntaje cae
 * por debajo del umbral configurado por el instructor.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record SubmissionResult(
        /** Identificador único de la submission calificada. */
        UUID                   submissionId,

        /** Puntaje bruto obtenido por el estudiante. */
        BigDecimal             score,

        /** Puntaje máximo posible para la evaluación. */
        BigDecimal             maxScore,

        /** {@code true} si el porcentaje de aciertos supera el umbral de aprobación. */
        boolean                passed,

        /** Porcentaje de puntaje obtenido (0-100). */
        double                 scorePct,

        /**
         * Recomendación del motor adaptativo, o {@code null} si el estudiante
         * superó el umbral configurado.
         */
        AdaptiveRecommendation recommendation
) {}
