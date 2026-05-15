package com.puj.users.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(min = 8, max = 100) String newPassword
) {}
