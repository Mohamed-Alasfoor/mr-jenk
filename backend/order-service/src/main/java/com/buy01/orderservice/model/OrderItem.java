package com.buy01.orderservice.model;
import java.math.BigDecimal;
public class OrderItem {
    private String productId,sellerId,productName,category; private BigDecimal unitPrice; private int quantity;
    public String getProductId(){return productId;} public void setProductId(String v){productId=v;}
    public String getSellerId(){return sellerId;} public void setSellerId(String v){sellerId=v;}
    public String getProductName(){return productName;} public void setProductName(String v){productName=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public BigDecimal getUnitPrice(){return unitPrice;} public void setUnitPrice(BigDecimal v){unitPrice=v;}
    public int getQuantity(){return quantity;} public void setQuantity(int v){quantity=v;}
    public BigDecimal subtotal(){return unitPrice.multiply(BigDecimal.valueOf(quantity));}
}
