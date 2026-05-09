package com.buy01.mediaservice.controller;

import com.buy01.mediaservice.dto.MediaItemResponse;
import com.buy01.mediaservice.dto.UpdateMediaAssignmentRequest;
import com.buy01.mediaservice.security.AuthenticatedUser;
import com.buy01.mediaservice.service.MediaDownload;
import com.buy01.mediaservice.service.MediaService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/images")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public List<MediaItemResponse> getMyImages(
            Authentication authentication,
            @RequestParam(required = false) String productId
    ) {
        return mediaService.getUserImages(getAuthenticatedUser(authentication), productId);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaItemResponse uploadImage(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String productId
    ) {
        return mediaService.uploadImage(getAuthenticatedUser(authentication), authorizationHeader, file, productId);
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<Resource> downloadImage(@PathVariable String id) {
        MediaDownload download = mediaService.downloadImage(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.media().getContentType()))
                .contentLength(download.media().getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .eTag("\"" + download.media().getChecksum() + "\"")
                .lastModified(download.media().getCreatedAt().toEpochMilli())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.media().getOriginalFilename()).build().toString()
                )
                .body(download.resource());
    }

    @DeleteMapping("/images/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(
            @PathVariable String id,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        mediaService.deleteImage(id, getAuthenticatedUser(authentication), authorizationHeader);
    }

    @PutMapping("/images/{id}/assignment")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public MediaItemResponse updateImageAssignment(
            @PathVariable String id,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody UpdateMediaAssignmentRequest request
    ) {
        return mediaService.updateImageAssignment(
                id,
                getAuthenticatedUser(authentication),
                authorizationHeader,
                request.productId()
        );
    }

    private AuthenticatedUser getAuthenticatedUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
