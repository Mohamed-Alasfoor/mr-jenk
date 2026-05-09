package com.buy01.mediaservice.dto;

import jakarta.validation.constraints.Size;

public record UpdateMediaAssignmentRequest(
        @Size(max = 100) String productId
) {
}
