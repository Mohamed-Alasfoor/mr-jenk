package com.buy01.mediaservice.dto;

import java.time.Instant;

public record MediaItemResponse(
        String id,
        String productId,
        String imageUrl,
        String contentType,
        long sizeBytes,
        String originalFilename,
        Instant createdAt
) {
}
