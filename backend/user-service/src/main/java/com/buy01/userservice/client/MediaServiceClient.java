package com.buy01.userservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buy01.userservice.exception.RemoteServiceException;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MediaServiceClient {

    private static final Pattern LOCAL_MEDIA_PATH_PATTERN = Pattern.compile("^/(?:api/)?media/images/([^/]+)$");

    private final RestClient restClient;
    private final String mediaPublicBaseUrl;
    private final ObjectMapper objectMapper;

    public MediaServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.services.media.base-url}") String mediaServiceBaseUrl,
            @Value("${app.services.media.public-base-url}") String mediaPublicBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder
                .baseUrl(mediaServiceBaseUrl)
                .build();
        this.mediaPublicBaseUrl = trimTrailingSlash(mediaPublicBaseUrl);
        this.objectMapper = objectMapper;
    }

    public String uploadAvatar(String authorizationHeader, MultipartFile avatarFile) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", buildFilePart(avatarFile));

            MediaUploadResponse response = restClient.post()
                    .uri("/media/images")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(MediaUploadResponse.class);

            if (response == null || response.imageUrl() == null || response.imageUrl().isBlank()) {
                throw new RemoteServiceException("Media service did not return an avatar URL", null);
            }

            return response.imageUrl();
        } catch (RestClientResponseException exception) {
            throw mapException(exception);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException("Media service is unavailable", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read avatar file");
        }
    }

    public void deleteMediaIfManaged(String authorizationHeader, String mediaUrl) {
        String mediaId = extractManagedMediaId(mediaUrl);
        if (mediaId == null) {
            return;
        }

        try {
            restClient.delete()
                    .uri("/media/images/{id}", mediaId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 404 || status == 403) {
                return;
            }
            throw mapException(exception);
        } catch (ResourceAccessException exception) {
            throw new RemoteServiceException("Media service is unavailable", exception);
        }
    }

    private HttpEntity<ByteArrayResource> buildFilePart(MultipartFile avatarFile) throws IOException {
        String contentType = avatarFile.getContentType();

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType));
        partHeaders.setContentDispositionFormData("file", avatarFile.getOriginalFilename());

        ByteArrayResource resource = new ByteArrayResource(avatarFile.getBytes()) {
            @Override
            public String getFilename() {
                return avatarFile.getOriginalFilename();
            }
        };

        return new HttpEntity<>(resource, partHeaders);
    }

    private RuntimeException mapException(RestClientResponseException exception) {
        return switch (exception.getStatusCode().value()) {
            case 400 -> new IllegalArgumentException(extractMessage(exception, "Avatar upload failed"));
            case 415 -> new IllegalArgumentException(extractMessage(exception, "Avatar upload failed"));
            case 403 -> new org.springframework.security.access.AccessDeniedException("Forbidden");
            case 404 -> new IllegalArgumentException(extractMessage(exception, "Avatar media was not found"));
            default -> new RemoteServiceException("Media service request failed: HTTP " + exception.getStatusCode(), exception);
        };
    }

    private String extractManagedMediaId(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return null;
        }

        String normalizedUrl = mediaUrl.trim();
        String prefix = mediaPublicBaseUrl + "/";
        if (!normalizedUrl.startsWith(prefix)) {
            return extractLegacyLocalMediaId(normalizedUrl);
        }

        String mediaId = normalizedUrl.substring(prefix.length());
        if (mediaId.isBlank() || mediaId.contains("/")) {
            return null;
        }

        return mediaId;
    }

    private String extractLegacyLocalMediaId(String mediaUrl) {
        try {
            URI uri = URI.create(mediaUrl);
            if (uri.getHost() == null || uri.getPath() == null) {
                return null;
            }

            String normalizedHost = uri.getHost().toLowerCase(Locale.ROOT);
            if (!"localhost".equals(normalizedHost) && !"127.0.0.1".equals(normalizedHost)) {
                return null;
            }

            Matcher matcher = LOCAL_MEDIA_PATH_PATTERN.matcher(uri.getPath());
            if (!matcher.matches()) {
                return null;
            }

            String mediaId = matcher.group(1);
            return mediaId.isBlank() ? null : mediaId;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Media public base URL must be configured");
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
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

    private record MediaUploadResponse(String imageUrl) {
    }
}
