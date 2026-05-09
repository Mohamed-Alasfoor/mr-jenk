package com.buy01.productservice.controller;

import com.buy01.productservice.dto.UpdateProductImagesRequest;
import com.buy01.productservice.security.AuthenticatedUser;
import com.buy01.productservice.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private final ProductService productService;

    public InternalProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}/ownership")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assertOwnership(@PathVariable String id, Authentication authentication) {
        productService.assertCanManageProduct(id, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PutMapping("/{id}/images")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceProductImages(
            @PathVariable String id,
            Authentication authentication,
            @Valid @RequestBody UpdateProductImagesRequest request
    ) {
        productService.replaceProductImages(id, (AuthenticatedUser) authentication.getPrincipal(), request.imageUrls());
    }
}
