package com.buy01.mediaservice.exception;

public class ReferencedProductNotFoundException extends RuntimeException {

    public ReferencedProductNotFoundException(String productId) {
        super("Referenced product not found or not owned: " + productId);
    }
}
