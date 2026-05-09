package com.buy01.mediaservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImageTypeSnifferTest {

    private final ImageTypeSniffer imageTypeSniffer = new ImageTypeSniffer();

    @Test
    void detectContentTypeReturnsPngForPngSignature() {
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };

        Optional<String> contentType = imageTypeSniffer.detectContentType(pngBytes);

        assertThat(contentType).contains("image/png");
    }

    @Test
    void detectContentTypeReturnsEmptyForUnknownSignature() {
        byte[] bytes = "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Optional<String> contentType = imageTypeSniffer.detectContentType(bytes);

        assertThat(contentType).isEmpty();
    }
}
