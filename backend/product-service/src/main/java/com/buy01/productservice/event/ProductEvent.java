package com.buy01.productservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductEvent(
        String eventType,
        String productId,
        String sellerId,
        String name,
        BigDecimal price,
        int quantity,
        List<String> imageUrls,
        Instant occurredAt
) {
}
