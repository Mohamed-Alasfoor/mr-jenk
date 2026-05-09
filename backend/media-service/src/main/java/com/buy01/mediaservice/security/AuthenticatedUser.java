package com.buy01.mediaservice.security;

import java.security.Principal;

public record AuthenticatedUser(
        String userId,
        String email,
        String role
) implements Principal {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    @Override
    public String getName() {
        return userId;
    }
}
