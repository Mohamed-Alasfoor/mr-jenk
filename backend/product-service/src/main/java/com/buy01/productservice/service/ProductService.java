package com.buy01.productservice.service;

import com.buy01.productservice.client.MediaServiceClient;
import com.buy01.productservice.dto.ProductRequest;
import com.buy01.productservice.dto.ProductResponse;
import com.buy01.productservice.event.ProductEventPublisher;
import com.buy01.productservice.exception.ProductNotFoundException;
import com.buy01.productservice.model.Product;
import com.buy01.productservice.repository.ProductRepository;
import com.buy01.productservice.security.AuthenticatedUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MediaServiceClient mediaServiceClient;
    private final ProductEventPublisher productEventPublisher;

    public ProductService(
            ProductRepository productRepository,
            MediaServiceClient mediaServiceClient,
            ProductEventPublisher productEventPublisher
    ) {
        this.productRepository = productRepository;
        this.mediaServiceClient = mediaServiceClient;
        this.productEventPublisher = productEventPublisher;
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ProductResponse getProductById(String id) {
        return mapToResponse(findById(id));
    }

    public List<ProductResponse> getProductsBySellerId(String sellerId) {
        return productRepository.findBySellerIdOrderByCreatedAtDesc(sellerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<ProductResponse> getSellerProducts(AuthenticatedUser user) {
        List<Product> products = user.isAdmin()
                ? productRepository.findAllByOrderByCreatedAtDesc()
                : productRepository.findBySellerIdOrderByCreatedAtDesc(user.userId());

        return products
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ProductResponse createProduct(AuthenticatedUser user, String authorizationHeader, ProductRequest request) {
        Instant now = Instant.now();
        List<String> normalizedImageUrls = normalizeImageUrls(request.imageUrls());

        Product product = new Product();
        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());
        product.setImageUrls(List.of());
        product.setSellerId(user.userId());
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product savedProduct = productRepository.save(product);
        try {
            List<String> synchronizedImageUrls = mediaServiceClient.syncProductImages(
                    authorizationHeader,
                    savedProduct.getId(),
                    normalizedImageUrls
            );
            savedProduct.setImageUrls(synchronizedImageUrls);
            savedProduct.setUpdatedAt(Instant.now());
            Product persistedProduct = productRepository.save(savedProduct);
            productEventPublisher.publishCreated(persistedProduct);
            return mapToResponse(persistedProduct);
        } catch (RuntimeException exception) {
            productRepository.delete(savedProduct);
            throw exception;
        }
    }

    public ProductResponse updateProduct(
            String productId,
            AuthenticatedUser user,
            String authorizationHeader,
            ProductRequest request
    ) {
        Product product = findManagedProduct(productId, user);
        List<String> synchronizedImageUrls = mediaServiceClient.syncProductImages(
                authorizationHeader,
                product.getId(),
                normalizeImageUrls(request.imageUrls())
        );

        applyRequest(product, request, synchronizedImageUrls);
        product.setUpdatedAt(Instant.now());
        Product persistedProduct = productRepository.save(product);
        productEventPublisher.publishUpdated(persistedProduct);
        return mapToResponse(persistedProduct);
    }

    public void deleteProduct(String productId, AuthenticatedUser user, String authorizationHeader) {
        Product product = findManagedProduct(productId, user);
        mediaServiceClient.clearProductImages(authorizationHeader, product.getId());
        productRepository.delete(product);
        productEventPublisher.publishDeleted(product);
    }

    public void replaceProductImages(String productId, AuthenticatedUser user, List<String> imageUrls) {
        Product product = findManagedProduct(productId, user);
        product.setImageUrls(normalizeImageUrls(imageUrls));
        product.setUpdatedAt(Instant.now());
        Product persistedProduct = productRepository.save(product);
        productEventPublisher.publishUpdated(persistedProduct);
    }

    public void assertCanManageProduct(String productId, AuthenticatedUser user) {
        findManagedProduct(productId, user);
    }

    private Product findById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Product findManagedProduct(String id, AuthenticatedUser user) {
        Product product = findById(id);
        if (!user.isAdmin() && !user.userId().equals(product.getSellerId())) {
            throw new AccessDeniedException("Forbidden");
        }
        return product;
    }

    private void applyRequest(Product product, ProductRequest request, List<String> imageUrls) {
        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());
        product.setImageUrls(imageUrls);
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return new ArrayList<>();
        }

        return imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private ProductResponse mapToResponse(Product product) {
        List<String> imageUrls = product.getImageUrls() == null ? List.of() : List.copyOf(product.getImageUrls());
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getSellerId(),
                imageUrls,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
