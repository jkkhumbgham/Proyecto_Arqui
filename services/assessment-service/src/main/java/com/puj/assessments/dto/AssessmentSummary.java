package com.puj.assessments.dto;

import com.puj.assessments.entity.Assessment;

import java.util.UUID;

public record AssessmentSummary(
        UUID   id,
        String title,
        UUID   courseId,
        UUID   lessonId,
        double passingScorePct,
        int    maxAttempts,
        double totalPoints
) {
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
