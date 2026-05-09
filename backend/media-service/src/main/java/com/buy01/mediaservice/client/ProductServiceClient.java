package com.buy01.mediaservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buy01.mediaservice.exception.ReferencedProductNotFoundException;
import com.buy01.mediaservice.exception.RemoteServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ProductServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProductServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.services.product.base-url}") String productServiceBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder
                .baseUrl(productServiceBaseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public void assertProductOwnership(String authorizationHeader, String productId) {
        try {
            restClient.get()
                    .uri("/internal/products/{id}/ownership", productId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw mapException(exception, productId);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException("Product service is unavailable", exception);
        }
    }

    public void replaceProductImages(String authorizationHeader, String productId, java.util.List<String> imageUrls) {
        try {
            restClient.put()
                    .uri("/internal/products/{id}/images", productId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .body(new UpdateProductImagesRequest(imageUrls))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw mapException(exception, productId);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException("Product service is unavailable", exception);
        }
    }

    private RuntimeException mapException(RestClientResponseException exception, String productId) {
        return switch (exception.getStatusCode().value()) {
            case 400 -> new IllegalArgumentException(extractMessage(exception, "Referenced product is invalid"));
            case 403 -> new org.springframework.security.access.AccessDeniedException("Forbidden");
            case 404 -> new ReferencedProductNotFoundException(productId);
            default -> new RemoteServiceException("Product service request failed: HTTP " + exception.getStatusCode(), exception);
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

    private record UpdateProductImagesRequest(java.util.List<String> imageUrls) {
    }
}
