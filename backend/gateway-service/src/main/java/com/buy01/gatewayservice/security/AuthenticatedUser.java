package com.buy01.gatewayservice.security;

public record AuthenticatedUser(
        String userId,
        String email,
        String role
) {
}
