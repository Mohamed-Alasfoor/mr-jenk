package com.buy01.mediaservice.service;

import org.springframework.core.io.Resource;

public interface ObjectStorageService {

    void store(String storageFilename, byte[] bytes);

    Resource loadAsResource(String storageFilename);

    boolean exists(String storageFilename);

    void delete(String storageFilename);
}
