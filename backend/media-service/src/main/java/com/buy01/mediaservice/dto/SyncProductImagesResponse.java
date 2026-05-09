package com.buy01.mediaservice.dto;

import java.util.List;

public record SyncProductImagesResponse(
        List<String> imageUrls
) {
}
