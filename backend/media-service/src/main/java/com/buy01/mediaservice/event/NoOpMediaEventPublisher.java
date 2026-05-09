package com.buy01.mediaservice.event;

import com.buy01.mediaservice.model.MediaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMediaEventPublisher implements MediaEventPublisher {

    @Override
    public void publishUploaded(MediaObject mediaObject, String imageUrl) {
    }

    @Override
    public void publishDeleted(MediaObject mediaObject, String imageUrl) {
    }
}
