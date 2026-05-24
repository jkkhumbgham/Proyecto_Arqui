package com.puj.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminCreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank        String password,
        @NotBlank        String firstName,
        @NotBlank        String lastName,
                         String role,
                         boolean consentGiven
) {}
