package com.puj.assessments.dto;

import com.puj.assessments.adaptive.AdaptiveRecommendation;

import java.math.BigDecimal;
import java.util.UUID;

public record SubmissionResult(
        UUID                    submissionId,
        BigDecimal              score,
        BigDecimal              maxScore,
        boolean                 passed,
        double                  scorePct,
        AdaptiveRecommendation  recommendation
) {}
