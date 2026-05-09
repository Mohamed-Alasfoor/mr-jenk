package com.buy01.productservice.dto;

import java.util.List;

public record SyncProductImagesResponse(
        List<String> imageUrls
) {
}
