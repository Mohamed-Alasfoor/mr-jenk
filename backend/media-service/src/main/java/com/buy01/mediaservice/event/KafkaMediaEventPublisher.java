package com.buy01.mediaservice.event;

import com.buy01.mediaservice.model.MediaObject;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaMediaEventPublisher implements MediaEventPublisher {

    private final KafkaTemplate<String, MediaEvent> kafkaTemplate;
    private final String topicName;

    public KafkaMediaEventPublisher(
            KafkaTemplate<String, MediaEvent> kafkaTemplate,
            @Value("${app.kafka.topics.media}") String topicName
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    @Override
    public void publishUploaded(MediaObject mediaObject, String imageUrl) {
        publish("IMAGE_UPLOADED", mediaObject, imageUrl);
    }

    @Override
    public void publishDeleted(MediaObject mediaObject, String imageUrl) {
        publish("IMAGE_DELETED", mediaObject, imageUrl);
    }

    private void publish(String eventType, MediaObject mediaObject, String imageUrl) {
        kafkaTemplate.send(topicName, mediaObject.getId(), new MediaEvent(
                eventType,
                mediaObject.getId(),
                mediaObject.getOwnerId(),
                mediaObject.getProductId(),
                mediaObject.getContentType(),
                mediaObject.getSizeBytes(),
                imageUrl,
                Instant.now()
        ));
    }
}
