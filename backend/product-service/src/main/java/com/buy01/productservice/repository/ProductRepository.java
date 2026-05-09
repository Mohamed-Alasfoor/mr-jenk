package com.buy01.productservice.repository;

import com.buy01.productservice.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findAllByOrderByCreatedAtDesc();

    List<Product> findBySellerIdOrderByCreatedAtDesc(String sellerId);

    Optional<Product> findByIdAndSellerId(String id, String sellerId);
}
