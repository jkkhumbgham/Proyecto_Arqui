package com.puj.users.dto;

import com.puj.security.rbac.Role;
import com.puj.users.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID    id,
        String  email,
        String  firstName,
        String  lastName,
        Role    role,
        boolean active,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
