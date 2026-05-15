package com.puj.collaboration.dto;

import jakarta.validation.constraints.NotBlank;

public record PostRequest(@NotBlank String content) {}
