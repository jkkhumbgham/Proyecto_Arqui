package com.puj.assessments.dto;

import com.puj.assessments.entity.Assessment;
import com.puj.assessments.entity.Question;

import java.util.List;
import java.util.UUID;

/**
 * Vista detallada de una evaluación, incluyendo todas sus preguntas y opciones.
 *
 * <p>Se usa para devolver al cliente la estructura completa de la evaluación.
 * Las respuestas correctas se incluyen para el uso del instructor; el frontend
 * es responsable de ocultarlas al estudiante según el rol.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public record AssessmentDetail(
        UUID             id,
        String           title,
        String           description,
        UUID             courseId,
        UUID             lessonId,
        double           passingScorePct,
        int              maxAttempts,
        Integer          timeLimitMinutes,
        List<QuestionDetail> questions
) {

    /**
     * Vista de una opción de respuesta para la representación detallada.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record OptionDetail(
            UUID   id,
            String text,
            int    orderIndex,
            boolean correct
    ) {}

    /**
     * Vista de una pregunta con sus opciones para la representación detallada.
     *
     * @author Plataforma PUJ
     * @since  1.0
     */
    public record QuestionDetail(
            UUID               id,
            String             questionText,
            String             questionType,
            double             points,
            int                orderIndex,
            List<OptionDetail> options
    ) {}

    /**
     * Construye un {@link AssessmentDetail} a partir de la entidad {@link Assessment}.
     *
     * <p>Filtra las preguntas eliminadas (soft-deleted) y mapea cada pregunta
     * con sus opciones al formato de presentación.</p>
     *
     * @param a entidad de evaluación a proyectar
     * @return instancia de {@link AssessmentDetail} con la vista completa
     */
    public static AssessmentDetail from(Assessment a) {
        List<QuestionDetail> qds = a.getQuestions().stream()
                .filter(q -> !q.isDeleted())
                .map(AssessmentDetail::toQuestionDetail)
                .toList();

        return new AssessmentDetail(
                a.getId(), a.getTitle(), a.getDescription(),
                a.getCourseId(), a.getLessonId(),
                a.getPassingScorePct(), a.getMaxAttempts(),
                a.getTimeLimitMinutes(), qds
        );
    }

    /**
     * Mapea una entidad {@link Question} a su DTO de presentación.
     *
     * @param q entidad de pregunta a mapear
     * @return {@link QuestionDetail} con las opciones de la pregunta
     */
    private static QuestionDetail toQuestionDetail(Question q) {
        List<OptionDetail> opts = q.getOptions().stream()
                .map(o -> new OptionDetail(
                        o.getId(), o.getText(), o.getOrderIndex(), o.isCorrect()))
                .toList();
        return new QuestionDetail(
                q.getId(), q.getText(), q.getType().name(),
                q.getPoints(), q.getOrderIndex(), opts
        );
    }
}
