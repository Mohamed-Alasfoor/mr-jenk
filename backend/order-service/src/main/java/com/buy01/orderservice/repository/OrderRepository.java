package com.buy01.orderservice.repository;
import com.buy01.orderservice.model.Order;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface OrderRepository extends MongoRepository<Order,String>{
    List<Order> findByBuyerIdOrderByCreatedAtDesc(String id);
    List<Order> findBySellerIdsContainingOrderByCreatedAtDesc(String id);
}
