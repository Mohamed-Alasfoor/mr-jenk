package com.buy01.productservice.event;

import com.buy01.productservice.model.Product;

public interface ProductEventPublisher {

    void publishCreated(Product product);

    void publishUpdated(Product product);

    void publishDeleted(Product product);
}
