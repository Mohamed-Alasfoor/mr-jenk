package com.buy01.productservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buy01.productservice.dto.SyncProductImagesRequest;
import com.buy01.productservice.dto.SyncProductImagesResponse;
import com.buy01.productservice.exception.ReferencedMediaNotFoundException;
import com.buy01.productservice.exception.RemoteServiceException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MediaServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MediaServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.services.media.base-url}") String mediaServiceBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder
                .baseUrl(mediaServiceBaseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public List<String> syncProductImages(String authorizationHeader, String productId, List<String> imageUrls) {
        try {
            SyncProductImagesResponse response = restClient.post()
                    .uri("/internal/media/images/sync-product")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .body(new SyncProductImagesRequest(productId, imageUrls))
                    .retrieve()
                    .body(SyncProductImagesResponse.class);

            return response == null || response.imageUrls() == null ? List.of() : response.imageUrls();
        } catch (RestClientResponseException exception) {
            throw mapException(exception);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException("Media service is unavailable", exception);
        }
    }

    public void clearProductImages(String authorizationHeader, String productId) {
        syncProductImages(authorizationHeader, productId, List.of());
    }

    private RuntimeException mapException(RestClientResponseException exception) {
        return switch (exception.getStatusCode().value()) {
            case 400 -> new IllegalArgumentException(extractMessage(exception, "Related media validation failed"));
            case 403 -> new org.springframework.security.access.AccessDeniedException("Forbidden");
            case 404 -> new ReferencedMediaNotFoundException(extractMessage(
                    exception,
                    "Referenced media was not found or is not owned by the current seller"
            ));
            default -> new RemoteServiceException("Media service request failed: HTTP " + exception.getStatusCode(), exception);
        };
    }

    private String extractMessage(RestClientResponseException exception, String fallback) {
        String responseBody = exception.getResponseBodyAsString();
        if (responseBody == null || responseBody.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.get("message");
            if (message != null && !message.isNull() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }
}
