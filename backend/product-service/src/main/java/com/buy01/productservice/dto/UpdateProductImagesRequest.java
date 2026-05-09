package com.buy01.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProductImagesRequest(
        List<@NotBlank @Size(max = 500) String> imageUrls
) {
}
