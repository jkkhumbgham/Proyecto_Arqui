package com.puj.assessments.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record SubmitRequest(
        @NotNull Map<String, List<String>> answers,
        long durationSeconds
) {}
