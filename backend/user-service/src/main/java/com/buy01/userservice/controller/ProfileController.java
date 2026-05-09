package com.buy01.userservice.controller;

import com.buy01.userservice.dto.ProfileResponse;
import com.buy01.userservice.dto.PublicSellerProfileResponse;
import com.buy01.userservice.dto.UpdateProfileRequest;
import com.buy01.userservice.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ProfileResponse getCurrentUser(Authentication authentication) {
        return userService.getProfile(authentication.getName());
    }

    @GetMapping("/sellers/{sellerId}")
    public PublicSellerProfileResponse getSellerProfile(@PathVariable String sellerId) {
        return userService.getPublicSellerProfile(sellerId);
    }

    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse updateCurrentUser(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(authentication.getName(), request);
    }

    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse updateCurrentUserWithAvatar(
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestPart("fullName") @NotBlank @Size(max = 100) String fullName,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar
    ) {
        return userService.updateProfileWithAvatar(authentication.getName(), authorizationHeader, fullName, avatar);
    }
}
