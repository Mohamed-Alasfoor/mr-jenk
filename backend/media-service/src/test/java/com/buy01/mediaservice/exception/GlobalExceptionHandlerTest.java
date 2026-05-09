package com.buy01.mediaservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unsupportedMediaMapsToUnsupportedMediaType() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnsupportedMedia(
                new UnsupportedMediaException("Only image uploads are allowed")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Only image uploads are allowed");
    }
}
