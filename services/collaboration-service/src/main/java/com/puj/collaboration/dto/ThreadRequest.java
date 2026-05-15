package com.puj.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThreadRequest(
        @NotBlank @Size(max = 300) String title,
        @NotBlank String content
) {}
