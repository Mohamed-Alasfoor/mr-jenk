package com.buy01.mediaservice.service;

import com.buy01.mediaservice.client.ProductServiceClient;
import com.buy01.mediaservice.dto.MediaItemResponse;
import com.buy01.mediaservice.event.MediaEventPublisher;
import com.buy01.mediaservice.exception.InvalidMediaException;
import com.buy01.mediaservice.exception.MediaNotFoundException;
import com.buy01.mediaservice.exception.UnsupportedMediaException;
import com.buy01.mediaservice.model.MediaObject;
import com.buy01.mediaservice.repository.MediaObjectRepository;
import com.buy01.mediaservice.security.AuthenticatedUser;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    private static final Pattern LOCAL_MEDIA_PATH_PATTERN = Pattern.compile("^/(?:api/)?media/images/([^/]+)$");

    private final MediaObjectRepository mediaObjectRepository;
    private final ObjectStorageService objectStorageService;
    private final ImageTypeSniffer imageTypeSniffer;
    private final ProductServiceClient productServiceClient;
    private final MediaEventPublisher mediaEventPublisher;
    private final long maxFileSizeBytes;
    private final String publicBaseUrl;

    public MediaService(
            MediaObjectRepository mediaObjectRepository,
            ObjectStorageService objectStorageService,
            ImageTypeSniffer imageTypeSniffer,
            ProductServiceClient productServiceClient,
            MediaEventPublisher mediaEventPublisher,
            @Value("${app.media.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.media.public-base-url}") String publicBaseUrl
    ) {
        this.mediaObjectRepository = mediaObjectRepository;
        this.objectStorageService = objectStorageService;
        this.imageTypeSniffer = imageTypeSniffer;
        this.productServiceClient = productServiceClient;
        this.mediaEventPublisher = mediaEventPublisher;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    public MediaItemResponse uploadImage(
            AuthenticatedUser user,
            String authorizationHeader,
            MultipartFile file,
            String productId
    ) {
        String normalizedProductId = normalizeOptional(productId);
        if (normalizedProductId != null) {
            productServiceClient.assertProductOwnership(authorizationHeader, normalizedProductId);
        }

        validateFile(file);

        String declaredContentType = file.getContentType();
        if (declaredContentType == null || !declaredContentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new UnsupportedMediaException("Only image uploads are allowed");
        }

        byte[] bytes = readBytes(file);
        String sniffedContentType = imageTypeSniffer.detectContentType(bytes)
                .orElseThrow(() -> new InvalidMediaException("Uploaded file content is not a supported image"));

        String storageFilename = UUID.randomUUID() + extensionFor(sniffedContentType);
        objectStorageService.store(storageFilename, bytes);

        MediaObject mediaObject = new MediaObject();
        mediaObject.setOwnerId(user.userId());
        mediaObject.setOwnerEmail(user.email());
        mediaObject.setProductId(normalizedProductId);
        mediaObject.setOriginalFilename(sanitizeOriginalFilename(file.getOriginalFilename()));
        mediaObject.setStorageFilename(storageFilename);
        mediaObject.setContentType(sniffedContentType);
        mediaObject.setSizeBytes(bytes.length);
        mediaObject.setChecksum(sha256(bytes));
        mediaObject.setCreatedAt(Instant.now());

        MediaObject savedMediaObject = null;
        try {
            savedMediaObject = mediaObjectRepository.save(mediaObject);
            syncProductImageListIfAssigned(authorizationHeader, normalizedProductId);
            MediaItemResponse response = mapToResponse(savedMediaObject);
            mediaEventPublisher.publishUploaded(savedMediaObject, response.imageUrl());
            return response;
        } catch (RuntimeException exception) {
            if (savedMediaObject != null && savedMediaObject.getId() != null) {
                mediaObjectRepository.delete(savedMediaObject);
            }
            objectStorageService.delete(storageFilename);
            throw exception;
        }
    }

    public List<MediaItemResponse> getUserImages(AuthenticatedUser user, String productId) {
        String normalizedProductId = normalizeOptional(productId);
        List<MediaObject> items;
        if (user.isAdmin()) {
            items = normalizedProductId == null
                    ? mediaObjectRepository.findAllByOrderByCreatedAtDesc()
                    : mediaObjectRepository.findByProductIdOrderByCreatedAtDesc(normalizedProductId);
        } else {
            items = normalizedProductId == null
                    ? mediaObjectRepository.findByOwnerIdOrderByCreatedAtDesc(user.userId())
                    : mediaObjectRepository.findByOwnerIdAndProductIdOrderByCreatedAtDesc(user.userId(), normalizedProductId);
        }

        return items.stream()
                .map(this::mapToResponse)
                .toList();
    }

    public MediaDownload downloadImage(String mediaId) {
        MediaObject mediaObject = mediaObjectRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));

        if (!objectStorageService.exists(mediaObject.getStorageFilename())) {
            throw new MediaNotFoundException(mediaId);
        }

        return new MediaDownload(mediaObject, objectStorageService.loadAsResource(mediaObject.getStorageFilename()));
    }

    public void deleteImage(String mediaId, AuthenticatedUser user, String authorizationHeader) {
        MediaObject mediaObject = findManagedMedia(mediaId, user);
        String affectedProductId = normalizeOptional(mediaObject.getProductId());
        String imageUrl = publicBaseUrl + "/" + mediaObject.getId();

        if (affectedProductId != null) {
            productServiceClient.replaceProductImages(
                    authorizationHeader,
                    affectedProductId,
                    getProductImageUrlsExcludingMedia(affectedProductId, mediaObject.getId())
            );
        }

        try {
            objectStorageService.delete(mediaObject.getStorageFilename());
            mediaObjectRepository.delete(mediaObject);
        } catch (RuntimeException exception) {
            syncProductImageListIfAssigned(authorizationHeader, affectedProductId);
            throw exception;
        }

        mediaEventPublisher.publishDeleted(mediaObject, imageUrl);
    }

    public MediaItemResponse updateImageAssignment(
            String mediaId,
            AuthenticatedUser user,
            String authorizationHeader,
            String productId
    ) {
        MediaObject mediaObject = findManagedMedia(mediaId, user);
        String previousProductId = normalizeOptional(mediaObject.getProductId());
        String nextProductId = normalizeOptional(productId);

        if (nextProductId != null) {
            productServiceClient.assertProductOwnership(authorizationHeader, nextProductId);
        }

        if (Objects.equals(previousProductId, nextProductId)) {
            return mapToResponse(mediaObject);
        }

        mediaObject.setProductId(nextProductId);

        try {
            MediaObject savedMediaObject = mediaObjectRepository.save(mediaObject);
            syncAffectedProductImages(authorizationHeader, previousProductId, nextProductId);
            return mapToResponse(savedMediaObject);
        } catch (RuntimeException exception) {
            mediaObject.setProductId(previousProductId);
            mediaObjectRepository.save(mediaObject);
            throw exception;
        }
    }

    public List<String> syncProductImages(AuthenticatedUser user, String productId, List<String> imageUrls) {
        String normalizedProductId = normalizeRequired(productId, "Product id is required");
        List<String> normalizedImageUrls = normalizeImageUrls(imageUrls);
        Map<String, String> mediaIdsByUrl = toMediaIdsByUrl(normalizedImageUrls);

        List<MediaObject> currentAssociated = user.isAdmin()
                ? mediaObjectRepository.findByProductIdOrderByCreatedAtDesc(normalizedProductId)
                : mediaObjectRepository.findByOwnerIdAndProductIdOrderByCreatedAtDesc(user.userId(), normalizedProductId);

        Map<String, MediaObject> requestedMediaById = loadManagedMediaById(user, mediaIdsByUrl.values());
        Set<String> requestedIds = requestedMediaById.keySet();

        for (MediaObject mediaObject : requestedMediaById.values()) {
            mediaObject.setProductId(normalizedProductId);
        }

        for (MediaObject mediaObject : currentAssociated) {
            if (!requestedIds.contains(mediaObject.getId())) {
                mediaObject.setProductId(null);
            }
        }

        List<MediaObject> itemsToSave = new ArrayList<>();
        itemsToSave.addAll(requestedMediaById.values());
        itemsToSave.addAll(currentAssociated.stream()
                .filter(item -> !requestedIds.contains(item.getId()))
                .toList());

        if (!itemsToSave.isEmpty()) {
            mediaObjectRepository.saveAll(itemsToSave);
        }

        return normalizedImageUrls.stream()
                .map(mediaIdsByUrl::get)
                .map(id -> publicBaseUrl + "/" + id)
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("File is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidMediaException("File exceeds the 2 MB limit");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException exception) {
            throw new InvalidMediaException("Failed to read uploaded file");
        }
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename : "image";
        String cleaned = StringUtils.cleanPath(filename).replace("\\", "/");
        int lastSlash = cleaned.lastIndexOf('/');
        String baseName = lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
        if (baseName.isBlank()) {
            return "image";
        }
        return baseName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/bmp" -> ".bmp";
            case "image/webp" -> ".webp";
            default -> throw new InvalidMediaException("Unsupported image type");
        };
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRequired(String value, String message) {
        String normalizedValue = normalizeOptional(value);
        if (normalizedValue == null) {
            throw new InvalidMediaException(message);
        }
        return normalizedValue;
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }

        return imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, String> toMediaIdsByUrl(List<String> imageUrls) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String imageUrl : imageUrls) {
            result.put(imageUrl, extractMediaId(imageUrl));
        }
        return result;
    }

    private String extractMediaId(String imageUrl) {
        String normalizedImageUrl = normalizeRequired(imageUrl, "Image URL is required");
        String expectedPrefix = publicBaseUrl + "/";
        if (normalizedImageUrl.startsWith(expectedPrefix)) {
            String mediaId = normalizedImageUrl.substring(expectedPrefix.length());
            if (mediaId.isBlank() || mediaId.contains("/")) {
                throw new InvalidMediaException("Image URL is invalid: " + normalizedImageUrl);
            }

            return mediaId;
        }

        String relativeManagedMediaId = extractManagedMediaIdFromPath(normalizedImageUrl);
        if (relativeManagedMediaId != null) {
            return relativeManagedMediaId;
        }

        String legacyLocalMediaId = extractLegacyLocalMediaId(normalizedImageUrl);
        if (legacyLocalMediaId != null) {
            return legacyLocalMediaId;
        }

        throw new InvalidMediaException("Image URL is not managed by media-service: " + normalizedImageUrl);
    }

    private String extractManagedMediaIdFromPath(String path) {
        if (path == null) {
            return null;
        }

        Matcher matcher = LOCAL_MEDIA_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) {
            return null;
        }

        String mediaId = matcher.group(1);
        if (mediaId.isBlank() || mediaId.contains("/")) {
            return null;
        }

        return mediaId;
    }

    private String extractLegacyLocalMediaId(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            if (uri.getHost() == null || uri.getPath() == null) {
                return null;
            }

            String normalizedHost = uri.getHost().toLowerCase(Locale.ROOT);
            if (!"localhost".equals(normalizedHost) && !"127.0.0.1".equals(normalizedHost)) {
                return null;
            }

            return extractManagedMediaIdFromPath(uri.getPath());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Map<String, MediaObject> loadManagedMediaById(AuthenticatedUser user, java.util.Collection<String> mediaIds) {
        Map<String, MediaObject> mediaById = mediaObjectRepository.findAllById(mediaIds)
                .stream()
                .collect(Collectors.toMap(MediaObject::getId, media -> media));

        for (String mediaId : mediaIds) {
            MediaObject mediaObject = mediaById.get(mediaId);
            if (mediaObject == null) {
                throw new MediaNotFoundException(mediaId);
            }
            if (!user.isAdmin() && !user.userId().equals(mediaObject.getOwnerId())) {
                throw new AccessDeniedException("Forbidden");
            }
        }

        return mediaById;
    }

    private MediaObject findManagedMedia(String mediaId, AuthenticatedUser user) {
        MediaObject mediaObject = mediaObjectRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));
        if (!user.isAdmin() && !user.userId().equals(mediaObject.getOwnerId())) {
            throw new AccessDeniedException("Forbidden");
        }
        return mediaObject;
    }

    private MediaItemResponse mapToResponse(MediaObject mediaObject) {
        return new MediaItemResponse(
                mediaObject.getId(),
                mediaObject.getProductId(),
                publicBaseUrl + "/" + mediaObject.getId(),
                mediaObject.getContentType(),
                mediaObject.getSizeBytes(),
                mediaObject.getOriginalFilename(),
                mediaObject.getCreatedAt()
        );
    }

    private void syncAffectedProductImages(String authorizationHeader, String... productIds) {
        for (String productId : productIds) {
            syncProductImageListIfAssigned(authorizationHeader, productId);
        }
    }

    private void syncProductImageListIfAssigned(String authorizationHeader, String productId) {
        String normalizedProductId = normalizeOptional(productId);
        if (normalizedProductId == null) {
            return;
        }

        productServiceClient.replaceProductImages(
                authorizationHeader,
                normalizedProductId,
                getProductImageUrls(normalizedProductId)
        );
    }

    private List<String> getProductImageUrls(String productId) {
        return mediaObjectRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toImageUrl)
                .toList();
    }

    private List<String> getProductImageUrlsExcludingMedia(String productId, String excludedMediaId) {
        return mediaObjectRepository.findByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .filter(mediaObject -> !excludedMediaId.equals(mediaObject.getId()))
                .map(this::toImageUrl)
                .toList();
    }

    private String toImageUrl(MediaObject mediaObject) {
        return publicBaseUrl + "/" + mediaObject.getId();
    }

    private String trimTrailingSlash(String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(text -> text.endsWith("/") ? text.substring(0, text.length() - 1) : text)
                .orElseThrow(() -> new IllegalArgumentException("Media public base URL must be configured"));
    }
}
