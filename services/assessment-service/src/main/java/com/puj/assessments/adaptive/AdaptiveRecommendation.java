package com.puj.assessments.adaptive;

import java.util.UUID;

public record AdaptiveRecommendation(
        UUID   supplementaryLessonId,
        String message,
        double achievedScorePct,
        double requiredScorePct
) {}
