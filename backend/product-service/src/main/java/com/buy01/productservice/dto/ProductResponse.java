package com.buy01.productservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        int quantity,
        String sellerId,
        List<String> imageUrls,
        Instant createdAt,
        Instant updatedAt
) {
}
