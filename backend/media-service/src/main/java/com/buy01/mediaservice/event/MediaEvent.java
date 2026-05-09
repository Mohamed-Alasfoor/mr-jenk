package com.buy01.mediaservice.event;

import java.time.Instant;

public record MediaEvent(
        String eventType,
        String mediaId,
        String ownerId,
        String productId,
        String contentType,
        long sizeBytes,
        String imageUrl,
        Instant occurredAt
) {
}
