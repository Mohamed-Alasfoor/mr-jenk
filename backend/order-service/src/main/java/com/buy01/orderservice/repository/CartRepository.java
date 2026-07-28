package com.buy01.orderservice.repository;
import com.buy01.orderservice.model.Cart;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface CartRepository extends MongoRepository<Cart,String>{Optional<Cart> findByUserId(String userId);}
