package com.puj.assessments.dto;

import com.puj.assessments.entity.Assessment;
import com.puj.assessments.entity.Question;

import java.util.List;
import java.util.UUID;

public record AssessmentDetail(
        UUID         id,
        String       title,
        String       description,
        UUID         courseId,
        UUID         lessonId,
        double       passingScorePct,
        int          maxAttempts,
        Integer      timeLimitMinutes,
        List<QuestionDetail> questions
) {
    public record OptionDetail(UUID id, String text, int orderIndex, boolean correct) {}

    public record QuestionDetail(
            UUID   id,
            String questionText,
            String questionType,
            double points,
            int    orderIndex,
            List<OptionDetail> options
    ) {}

    public static AssessmentDetail from(Assessment a) {
        List<QuestionDetail> qds = a.getQuestions().stream()
                .filter(q -> !q.isDeleted())
                .map(q -> {
                    List<OptionDetail> opts = q.getOptions().stream()
                            .map(o -> new OptionDetail(o.getId(), o.getText(), o.getOrderIndex(), o.isCorrect()))
                            .toList();
                    return new QuestionDetail(
                            q.getId(), q.getText(), q.getType().name(),
                            q.getPoints(), q.getOrderIndex(), opts
                    );
                })
                .toList();

        return new AssessmentDetail(
                a.getId(), a.getTitle(), a.getDescription(),
                a.getCourseId(), a.getLessonId(),
                a.getPassingScorePct(), a.getMaxAttempts(), a.getTimeLimitMinutes(),
                qds
        );
    }
}
