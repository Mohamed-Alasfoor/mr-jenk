package com.buy01.mediaservice.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SyncProductImagesRequest(
        @NotBlank String productId,
        List<String> imageUrls
) {
}
