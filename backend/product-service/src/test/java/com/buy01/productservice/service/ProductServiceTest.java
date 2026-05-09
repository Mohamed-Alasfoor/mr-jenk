package com.buy01.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buy01.productservice.client.MediaServiceClient;
import com.buy01.productservice.dto.ProductRequest;
import com.buy01.productservice.dto.ProductResponse;
import com.buy01.productservice.event.ProductEventPublisher;
import com.buy01.productservice.exception.ProductNotFoundException;
import com.buy01.productservice.model.Product;
import com.buy01.productservice.repository.ProductRepository;
import com.buy01.productservice.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MediaServiceClient mediaServiceClient;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @InjectMocks
    private ProductService productService;

    @Test
    void createProductAssignsOwnershipToAuthenticatedSeller() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        ProductRequest request = new ProductRequest(
                "Phone",
                "Flagship phone",
                new BigDecimal("699.99"),
                5,
                List.of("http://localhost:8080/media/images/img-1")
        );

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) {
                product.setId("product-1");
            }
            product.setCreatedAt(Instant.now());
            product.setUpdatedAt(Instant.now());
            return product;
        });
        when(mediaServiceClient.syncProductImages(
                eq("Bearer token"),
                eq("product-1"),
                eq(List.of("http://localhost:8080/media/images/img-1"))
        )).thenReturn(List.of("http://localhost:8080/media/images/img-1"));

        ProductResponse response = productService.createProduct(user, "Bearer token", request);

        assertThat(response.id()).isEqualTo("product-1");
        assertThat(response.sellerId()).isEqualTo("seller-1");
        assertThat(response.name()).isEqualTo("Phone");
        assertThat(response.imageUrls()).containsExactly("http://localhost:8080/media/images/img-1");
    }

    @Test
    void updateProductRejectsNonOwner() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        ProductRequest request = new ProductRequest(
                "Phone",
                "Updated",
                new BigDecimal("499.99"),
                2,
                List.of()
        );
        Product existingProduct = new Product();
        existingProduct.setId("product-1");
        existingProduct.setSellerId("seller-2");

        when(productRepository.findById("product-1")).thenReturn(Optional.of(existingProduct));

        assertThatThrownBy(() -> productService.updateProduct("product-1", user, "Bearer token", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Forbidden");
    }

    @Test
    void updateProductRejectsMissingProduct() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        ProductRequest request = new ProductRequest(
                "Phone",
                "Updated",
                new BigDecimal("499.99"),
                2,
                List.of()
        );

        when(productRepository.findById("product-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct("product-1", user, "Bearer token", request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: product-1");
    }

    @Test
    void replaceProductImagesUpdatesManagedProduct() {
        AuthenticatedUser user = new AuthenticatedUser("seller-1", "seller@example.com", "SELLER");
        Product existingProduct = new Product();
        existingProduct.setId("product-1");
        existingProduct.setSellerId("seller-1");
        existingProduct.setName("Phone");
        existingProduct.setDescription("Flagship phone");
        existingProduct.setPrice(new BigDecimal("699.99"));
        existingProduct.setQuantity(5);
        existingProduct.setImageUrls(List.of("http://localhost:8080/media/images/img-old"));

        when(productRepository.findById("product-1")).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.replaceProductImages(
                "product-1",
                user,
                List.of("http://localhost:8080/media/images/img-1", "http://localhost:8080/media/images/img-2")
        );

        assertThat(existingProduct.getImageUrls()).containsExactly(
                "http://localhost:8080/media/images/img-1",
                "http://localhost:8080/media/images/img-2"
        );
        verify(productRepository).save(existingProduct);
        verify(productEventPublisher).publishUpdated(existingProduct);
    }

    @Test
    void getProductsBySellerIdReturnsOnlySellerCatalog() {
        Product sellerProduct = new Product();
        sellerProduct.setId("product-1");
        sellerProduct.setName("Phone");
        sellerProduct.setDescription("Flagship phone");
        sellerProduct.setPrice(new BigDecimal("699.99"));
        sellerProduct.setQuantity(5);
        sellerProduct.setSellerId("seller-1");
        sellerProduct.setImageUrls(List.of("http://localhost:8080/media/images/img-1"));
        sellerProduct.setCreatedAt(Instant.parse("2026-04-05T10:15:30Z"));
        sellerProduct.setUpdatedAt(Instant.parse("2026-04-05T10:15:30Z"));

        when(productRepository.findBySellerIdOrderByCreatedAtDesc("seller-1"))
                .thenReturn(List.of(sellerProduct));

        List<ProductResponse> response = productService.getProductsBySellerId("seller-1");

        assertThat(response)
                .hasSize(1)
                .first()
                .satisfies(product -> {
                    assertThat(product.id()).isEqualTo("product-1");
                    assertThat(product.sellerId()).isEqualTo("seller-1");
                });
    }
}
