package com.buy01.userservice.dto;

import com.buy01.userservice.model.Role;

public record ProfileResponse(
        String id,
        String email,
        String fullName,
        Role role,
        String avatarUrl
) {
}
