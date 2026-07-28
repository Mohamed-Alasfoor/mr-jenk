package com.buy01.orderservice.model;
public class CartItem {
    private String productId; private int quantity;
    public CartItem(){} public CartItem(String id,int qty){productId=id;quantity=qty;}
    public String getProductId(){return productId;} public void setProductId(String v){productId=v;}
    public int getQuantity(){return quantity;} public void setQuantity(int v){quantity=v;}
}
