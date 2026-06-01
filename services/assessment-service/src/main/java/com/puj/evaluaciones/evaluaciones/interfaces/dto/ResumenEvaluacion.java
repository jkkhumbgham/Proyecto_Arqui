package com.puj.evaluaciones.evaluaciones.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.puj.evaluaciones.evaluaciones.dominio.Evaluacion;

import java.util.UUID;

/**
 * Vista resumida de una evaluación para listados y tarjetas de curso.
 *
 * <p>No incluye preguntas ni opciones de respuesta; se usa en los endpoints
 * de listado para minimizar el tamaño de la respuesta JSON.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record ResumenEvaluacion(
        @JsonProperty("id")              UUID   id,
        @JsonProperty("title")           String titulo,
        @JsonProperty("courseId")        UUID   idCurso,
        @JsonProperty("lessonId")        UUID   idLeccion,
        @JsonProperty("passingScorePct") double porcentajeAprobacion,
        @JsonProperty("maxAttempts")     int    maxIntentos,
        /** Suma de puntos de todas las preguntas activas de la evaluación. */
        @JsonProperty("totalPoints")     double totalPuntos
) {

    /**
     * Construye un {@link ResumenEvaluacion} a partir de la entidad {@link Evaluacion}.
     *
     * <p>Calcula {@code totalPuntos} sumando los puntos de las preguntas no eliminadas.</p>
     *
     * @param e entidad de evaluación a proyectar
     * @return instancia de {@link ResumenEvaluacion} con la vista resumida
     */
    public static ResumenEvaluacion from(Evaluacion e) {
        double total = e.getPreguntas() == null ? 0 :
                e.getPreguntas().stream()
                        .filter(p -> !p.isEliminada())
                        .mapToDouble(p -> p.getPuntos())
                        .sum();
        return new ResumenEvaluacion(
                e.getId(), e.getTitulo(), e.getIdCurso(),
                e.getIdLeccion(), e.getPorcentajeAprobacion(), e.getMaxIntentos(),
                total
        );
    }
}
