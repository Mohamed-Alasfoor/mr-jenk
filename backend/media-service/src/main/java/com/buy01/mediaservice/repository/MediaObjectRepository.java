package com.buy01.mediaservice.repository;

import com.buy01.mediaservice.model.MediaObject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MediaObjectRepository extends MongoRepository<MediaObject, String> {

    List<MediaObject> findAllByOrderByCreatedAtDesc();

    List<MediaObject> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<MediaObject> findByProductIdOrderByCreatedAtDesc(String productId);

    List<MediaObject> findByOwnerIdAndProductIdOrderByCreatedAtDesc(String ownerId, String productId);

    Optional<MediaObject> findByIdAndOwnerId(String id, String ownerId);
}
