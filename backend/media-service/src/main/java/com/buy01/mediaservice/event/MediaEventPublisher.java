package com.buy01.mediaservice.event;

import com.buy01.mediaservice.model.MediaObject;

public interface MediaEventPublisher {

    void publishUploaded(MediaObject mediaObject, String imageUrl);

    void publishDeleted(MediaObject mediaObject, String imageUrl);
}
