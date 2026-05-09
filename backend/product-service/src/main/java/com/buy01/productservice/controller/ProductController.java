package com.buy01.productservice.controller;

import com.buy01.productservice.dto.ProductRequest;
import com.buy01.productservice.dto.ProductResponse;
import com.buy01.productservice.security.AuthenticatedUser;
import com.buy01.productservice.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> getProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/seller/{sellerId}")
    public List<ProductResponse> getProductsBySeller(@PathVariable String sellerId) {
        return productService.getProductsBySellerId(sellerId);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public List<ProductResponse> getMyProducts(Authentication authentication) {
        return productService.getSellerProducts(getAuthenticatedUser(authentication));
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable String id) {
        return productService.getProductById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody ProductRequest request
    ) {
        return productService.createProduct(getAuthenticatedUser(authentication), authorizationHeader, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ProductResponse updateProduct(
            @PathVariable String id,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody ProductRequest request
    ) {
        return productService.updateProduct(id, getAuthenticatedUser(authentication), authorizationHeader, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @PathVariable String id,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    ) {
        productService.deleteProduct(id, getAuthenticatedUser(authentication), authorizationHeader);
    }

    private AuthenticatedUser getAuthenticatedUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
