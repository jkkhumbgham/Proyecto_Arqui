package com.puj.assessments.adaptive;

import java.util.UUID;

/**
 * Resultado inmutable del motor adaptativo cuando el estudiante no supera el umbral.
 *
 * <p>Encapsula la lección suplementaria recomendada, el mensaje explicativo y
 * los porcentajes de puntaje alcanzado y requerido para que el frontend pueda
 * mostrar retroalimentación contextualizada al estudiante.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record AdaptiveRecommendation(
        /** Identificador de la lección de refuerzo a la que se redirige al estudiante. */
        UUID   supplementaryLessonId,

        /** Mensaje explicativo generado por el instructor o el motor adaptativo. */
        String message,

        /** Porcentaje de puntaje obtenido por el estudiante en la evaluación (0-100). */
        double achievedScorePct,

        /** Umbral mínimo configurado por el instructor (0-100). */
        double requiredScorePct
) {}
