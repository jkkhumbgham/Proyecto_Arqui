package com.puj.assessments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AssessmentCreateRequest(
        @NotBlank String title,
        @NotNull  UUID courseId,
        UUID lessonId,
        String description,
        Double passingScorePct,
        Integer maxAttempts,
        List<QuestionRequest> questions
) {
    public record QuestionRequest(
            @NotBlank String text,
            @NotBlank String type,
            double points,
            List<OptionRequest> options
    ) {}

    public record OptionRequest(
            @NotBlank String text,
            boolean correct
    ) {}
}
