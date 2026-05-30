package com.puj.evaluaciones.entregas.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.evaluaciones.adaptativo.dominio.RecomendacionAdaptativa;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado devuelto al estudiante tras calificar una entrega.
 *
 * <p>Incluye el puntaje obtenido, el máximo posible, si aprobó y,
 * opcionalmente, una recomendación adaptativa cuando el puntaje cae
 * por debajo del umbral configurado por el instructor.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record ResultadoEntrega(
        /** Identificador único de la entrega calificada. */
        @JsonProperty("submissionId")
        UUID idEntrega,

        /** Puntaje bruto obtenido por el estudiante. */
        @JsonProperty("score")
        BigDecimal puntuacion,

        /** Puntaje máximo posible para la evaluación. */
        @JsonProperty("maxScore")
        BigDecimal puntuacionMaxima,

        /** {@code true} si el porcentaje de aciertos supera el umbral de aprobación. */
        @JsonProperty("passed")
        boolean aprobado,

        /** Porcentaje de puntaje obtenido (0-100). */
        @JsonProperty("scorePct")
        double porcentajePuntaje,

        /**
         * Recomendación del motor adaptativo, o {@code null} si el estudiante
         * superó el umbral configurado.
         */
        @JsonProperty("recommendation")
        RecomendacionAdaptativa recomendacion
) {}
