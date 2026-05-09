package com.buy01.userservice.dto;

public record AuthResponse(
        String token,
        ProfileResponse user
) {
}
