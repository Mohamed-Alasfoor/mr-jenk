package com.buy01.orderservice.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
@Document("orders")
public class Order {
    @Id private String id; @Indexed private String buyerId; @Indexed private List<String> sellerIds=new ArrayList<>();
    private List<OrderItem> items=new ArrayList<>(); private String address,paymentMethod; private OrderStatus status;
    private BigDecimal total; private Instant createdAt,updatedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getBuyerId(){return buyerId;} public void setBuyerId(String v){buyerId=v;}
    public List<String> getSellerIds(){return sellerIds;} public void setSellerIds(List<String> v){sellerIds=v;}
    public List<OrderItem> getItems(){return items;} public void setItems(List<OrderItem> v){items=v;}
    public String getAddress(){return address;} public void setAddress(String v){address=v;}
    public String getPaymentMethod(){return paymentMethod;} public void setPaymentMethod(String v){paymentMethod=v;}
    public OrderStatus getStatus(){return status;} public void setStatus(OrderStatus v){status=v;}
    public BigDecimal getTotal(){return total;} public void setTotal(BigDecimal v){total=v;}
    public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
