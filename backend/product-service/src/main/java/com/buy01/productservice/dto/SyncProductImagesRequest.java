package com.buy01.productservice.dto;

import java.util.List;

public record SyncProductImagesRequest(
        String productId,
        List<String> imageUrls
) {
}
