package com.buy01.mediaservice.controller;

import com.buy01.mediaservice.dto.SyncProductImagesRequest;
import com.buy01.mediaservice.dto.SyncProductImagesResponse;
import com.buy01.mediaservice.security.AuthenticatedUser;
import com.buy01.mediaservice.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/media")
public class InternalMediaController {

    private final MediaService mediaService;

    public InternalMediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/images/sync-product")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public SyncProductImagesResponse syncProductImages(
            Authentication authentication,
            @Valid @RequestBody SyncProductImagesRequest request
    ) {
        return new SyncProductImagesResponse(
                mediaService.syncProductImages((AuthenticatedUser) authentication.getPrincipal(), request.productId(), request.imageUrls())
        );
    }
}
