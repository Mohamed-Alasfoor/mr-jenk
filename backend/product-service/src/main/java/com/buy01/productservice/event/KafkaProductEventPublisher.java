package com.buy01.productservice.event;

import com.buy01.productservice.model.Product;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaProductEventPublisher implements ProductEventPublisher {

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final String topicName;

    public KafkaProductEventPublisher(
            KafkaTemplate<String, ProductEvent> kafkaTemplate,
            @Value("${app.kafka.topics.products}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    @Override
    public void publishCreated(Product product) {
        publish("PRODUCT_CREATED", product);
    }

    @Override
    public void publishUpdated(Product product) {
        publish("PRODUCT_UPDATED", product);
    }

    @Override
    public void publishDeleted(Product product) {
        publish("PRODUCT_DELETED", product);
    }

    private void publish(String eventType, Product product) {
        kafkaTemplate.send(topicName, product.getId(), new ProductEvent(
                eventType,
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getPrice(),
                product.getQuantity(),
                product.getImageUrls() == null ? List.of() : List.copyOf(product.getImageUrls()),
                Instant.now()
        ));
    }
}
