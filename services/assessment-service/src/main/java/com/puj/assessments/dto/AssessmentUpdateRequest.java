package com.puj.assessments.dto;

import java.util.List;
import java.util.UUID;

public record AssessmentUpdateRequest(
        String  title,
        String  description,
        UUID    lessonId,
        Double  passingScorePct,
        Integer maxAttempts,
        List<AssessmentCreateRequest.QuestionRequest> questions
) {}
