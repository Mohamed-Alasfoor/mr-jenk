package com.buy01.orderservice.model;
import java.time.Instant;
import java.util.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("carts")
public class Cart {
    @Id private String id; @Indexed(unique=true) private String userId;
    private List<CartItem> items=new ArrayList<>(); private Instant updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
    public List<CartItem> getItems(){return items;} public void setItems(List<CartItem> v){items=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
