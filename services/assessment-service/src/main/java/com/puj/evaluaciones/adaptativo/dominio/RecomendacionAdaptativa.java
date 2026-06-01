package com.puj.evaluaciones.adaptativo.dominio;

import com.fasterxml.jackson.annotation.JsonProperty;

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
public record RecomendacionAdaptativa(
        /** Identificador de la lección de refuerzo a la que se redirige al estudiante. */
        @JsonProperty("supplementaryLessonId")
        UUID   idLeccionSupplementaria,

        /** Mensaje explicativo generado por el instructor o el motor adaptativo. */
        @JsonProperty("message")
        String mensaje,

        /** Porcentaje de puntaje obtenido por el estudiante en la evaluación (0-100). */
        @JsonProperty("achievedScorePct")
        double porcentajeObtenido,

        /** Umbral mínimo configurado por el instructor (0-100). */
        @JsonProperty("requiredScorePct")
        double porcentajeRequerido
) {}
