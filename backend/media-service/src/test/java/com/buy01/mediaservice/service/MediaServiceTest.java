package com.buy01.mediaservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buy01.mediaservice.client.ProductServiceClient;
import com.buy01.mediaservice.dto.MediaItemResponse;
import com.buy01.mediaservice.event.MediaEventPublisher;
import com.buy01.mediaservice.exception.UnsupportedMediaException;
import com.buy01.mediaservice.model.MediaObject;
import com.buy01.mediaservice.repository.MediaObjectRepository;
import com.buy01.mediaservice.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaObjectRepository mediaObjectRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private ImageTypeSniffer imageTypeSniffer;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private MediaEventPublisher mediaEventPublisher;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(
                mediaObjectRepository,
                objectStorageService,
                imageTypeSniffer,
                productServiceClient,
                mediaEventPublisher,
                2 * 1024 * 1024,
                "http://localhost:8080/media/images"
        );
    }

    @Test
    void uploadImageWithProductAssignmentSyncsProductImages() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3});
        MediaObject savedMedia = mediaObject("media-1", "seller-1", "product-1");

        when(imageTypeSniffer.detectContentType(file.getBytes())).thenReturn(Optional.of("image/png"));
        when(mediaObjectRepository.save(any(MediaObject.class))).thenAnswer(invocation -> {
            MediaObject mediaObject = invocation.getArgument(0);
            mediaObject.setId("media-1");
            return mediaObject;
        });
        when(mediaObjectRepository.findByProductIdOrderByCreatedAtDesc("product-1")).thenReturn(List.of(savedMedia));

        MediaItemResponse response = mediaService.uploadImage(user, "Bearer token", file, "product-1");

        assertThat(response.id()).isEqualTo("media-1");
        assertThat(response.productId()).isEqualTo("product-1");
        verify(productServiceClient).assertProductOwnership("Bearer token", "product-1");
        verify(productServiceClient).replaceProductImages(
                "Bearer token",
                "product-1",
                List.of("http://localhost:8080/media/images/media-1")
        );
    }

    @Test
    void updateImageAssignmentSyncsOldAndNewProducts() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        MediaObject mediaObject = mediaObject("media-1", "seller-1", "product-old");

        when(mediaObjectRepository.findById("media-1")).thenReturn(Optional.of(mediaObject));
        when(mediaObjectRepository.save(any(MediaObject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaObjectRepository.findByProductIdOrderByCreatedAtDesc("product-old")).thenReturn(List.of());
        when(mediaObjectRepository.findByProductIdOrderByCreatedAtDesc("product-new")).thenReturn(List.of(mediaObject));

        MediaItemResponse response = mediaService.updateImageAssignment(
                "media-1",
                user,
                "Bearer token",
                "product-new"
        );

        assertThat(response.productId()).isEqualTo("product-new");
        verify(productServiceClient).assertProductOwnership("Bearer token", "product-new");
        verify(productServiceClient).replaceProductImages("Bearer token", "product-old", List.of());
        verify(productServiceClient).replaceProductImages(
                "Bearer token",
                "product-new",
                List.of("http://localhost:8080/media/images/media-1")
        );
    }

    @Test
    void deleteImageSyncsAssignedProductBeforeRemovingMedia() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        MediaObject mediaObject = mediaObject("media-1", "seller-1", "product-1");

        when(mediaObjectRepository.findById("media-1")).thenReturn(Optional.of(mediaObject));
        when(mediaObjectRepository.findByProductIdOrderByCreatedAtDesc("product-1")).thenReturn(List.of(mediaObject));

        mediaService.deleteImage("media-1", user, "Bearer token");

        verify(productServiceClient).replaceProductImages("Bearer token", "product-1", List.of());
        verify(objectStorageService).delete(mediaObject.getStorageFilename());
        verify(mediaObjectRepository).delete(mediaObject);
    }

    @Test
    void syncProductImagesAcceptsLegacyLocalGatewayUrlsWhenPublicBaseUrlChanges() {
        MediaService httpsMediaService = new MediaService(
                mediaObjectRepository,
                objectStorageService,
                imageTypeSniffer,
                productServiceClient,
                mediaEventPublisher,
                2 * 1024 * 1024,
                "https://localhost/api/media/images"
        );

        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        MediaObject mediaObject = mediaObject("media-1", "seller-1", null);

        when(mediaObjectRepository.findByOwnerIdAndProductIdOrderByCreatedAtDesc("seller-1", "product-1"))
                .thenReturn(List.of());
        when(mediaObjectRepository.findAllById(any())).thenReturn(List.of(mediaObject));

        assertThatCode(() -> httpsMediaService.syncProductImages(
                user,
                "product-1",
                List.of("http://localhost:8080/media/images/media-1")
        )).doesNotThrowAnyException();

        ArgumentCaptor<Iterable<MediaObject>> itemsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(mediaObjectRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue())
                .singleElement()
                .extracting(MediaObject::getProductId)
                .isEqualTo("product-1");
    }

    @Test
    void syncProductImagesAcceptsRelativeGatewayUrls() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        MediaObject mediaObject = mediaObject("media-1", "seller-1", null);

        when(mediaObjectRepository.findByOwnerIdAndProductIdOrderByCreatedAtDesc("seller-1", "product-1"))
                .thenReturn(List.of());
        when(mediaObjectRepository.findAllById(any())).thenReturn(List.of(mediaObject));

        assertThatCode(() -> mediaService.syncProductImages(
                user,
                "product-1",
                List.of("/api/media/images/media-1")
        )).doesNotThrowAnyException();

        ArgumentCaptor<Iterable<MediaObject>> itemsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(mediaObjectRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue())
                .singleElement()
                .extracting(MediaObject::getProductId)
                .isEqualTo("product-1");
    }

    @Test
    void uploadImageRejectsNonImageMimeTypeWithUnsupportedMediaType() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mediaService.uploadImage(user, "Bearer token", file, null)
        )
                .isInstanceOf(UnsupportedMediaException.class)
                .hasMessage("Only image uploads are allowed");
    }

    @Test
    void syncProductImagesRejectsAnotherSellerMediaWithForbidden() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        MediaObject mediaObject = mediaObject("media-2", "seller-2", null);

        when(mediaObjectRepository.findByOwnerIdAndProductIdOrderByCreatedAtDesc("seller-1", "product-1"))
                .thenReturn(List.of());
        when(mediaObjectRepository.findAllById(any())).thenReturn(List.of(mediaObject));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mediaService.syncProductImages(
                        user,
                        "product-1",
                        List.of("http://localhost:8080/media/images/media-2")
                )
        )
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Forbidden");
    }

    @Test
    void deleteImageRejectsNonOwnerWhenMediaExists() {
        AuthenticatedUser user = new AuthenticatedUser("seller-2", "seller-two@example.com", "SELLER");
        MediaObject mediaObject = mediaObject("media-1", "seller-1", "product-1");

        when(mediaObjectRepository.findById("media-1")).thenReturn(Optional.of(mediaObject));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mediaService.deleteImage("media-1", user, "Bearer token")
        )
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Forbidden");
    }

    private MediaObject mediaObject(String id, String ownerId, String productId) {
        MediaObject mediaObject = new MediaObject();
        mediaObject.setId(id);
        mediaObject.setOwnerId(ownerId);
        mediaObject.setOwnerEmail(ownerId + "@example.com");
        mediaObject.setProductId(productId);
        mediaObject.setOriginalFilename("photo.png");
        mediaObject.setStorageFilename("stored-photo.png");
        mediaObject.setContentType("image/png");
        mediaObject.setSizeBytes(123);
        mediaObject.setChecksum("checksum");
        mediaObject.setCreatedAt(Instant.now());
        return mediaObject;
    }
}
