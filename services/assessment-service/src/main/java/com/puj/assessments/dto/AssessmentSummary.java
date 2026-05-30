package com.puj.assessments.dto;

import com.puj.assessments.entity.Assessment;

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
public record AssessmentSummary(
        UUID   id,
        String title,
        UUID   courseId,
        UUID   lessonId,
        double passingScorePct,
        int    maxAttempts,
        /** Suma de puntos de todas las preguntas activas de la evaluación. */
        double totalPoints
) {

    /**
     * Construye un {@link AssessmentSummary} a partir de la entidad {@link Assessment}.
     *
     * <p>Calcula {@code totalPoints} sumando los puntos de las preguntas no eliminadas.</p>
     *
     * @param a entidad de evaluación a proyectar
     * @return instancia de {@link AssessmentSummary} con la vista resumida
     */
    public static AssessmentSummary from(Assessment a) {
        double total = a.getQuestions() == null ? 0 :
                a.getQuestions().stream()
                        .filter(q -> !q.isDeleted())
                        .mapToDouble(q -> q.getPoints())
                        .sum();
        return new AssessmentSummary(
                a.getId(), a.getTitle(), a.getCourseId(),
                a.getLessonId(), a.getPassingScorePct(), a.getMaxAttempts(),
                total
        );
    }
}
