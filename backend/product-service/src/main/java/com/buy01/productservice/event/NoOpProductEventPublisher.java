package com.buy01.productservice.event;

import com.buy01.productservice.model.Product;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpProductEventPublisher implements ProductEventPublisher {

    @Override
    public void publishCreated(Product product) {
    }

    @Override
    public void publishUpdated(Product product) {
    }

    @Override
    public void publishDeleted(Product product) {
    }
}
