package com.puj.users.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long   expiresIn,
        String tokenType,
        UserResponse user
) {
    public static LoginResponse of(String accessToken, String refreshToken,
                                   long expiresIn, UserResponse user) {
        return new LoginResponse(accessToken, refreshToken, expiresIn, "Bearer", user);
    }
}
