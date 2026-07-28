package com.buy01.productservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductSearchResponse(
        List<ProductResponse> items,
        long totalElements,
        int totalPages,
        int page,
        int size,
        List<String> categories,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {}
