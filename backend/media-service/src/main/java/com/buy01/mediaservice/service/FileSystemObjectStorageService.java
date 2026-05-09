package com.buy01.mediaservice.service;

import com.buy01.mediaservice.exception.StorageOperationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "filesystem")
public class FileSystemObjectStorageService implements ObjectStorageService {

    private final Path rootDirectory;

    public FileSystemObjectStorageService(@Value("${app.storage.root-directory}") String rootDirectory) {
        this.rootDirectory = Path.of(rootDirectory).toAbsolutePath().normalize();
        initialize();
    }

    public void store(String storageFilename, byte[] bytes) {
        Path target = resolve(storageFilename);
        try {
            Files.createDirectories(rootDirectory);
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new StorageOperationException("Failed to store file", exception);
        }
    }

    public Resource loadAsResource(String storageFilename) {
        Path target = resolve(storageFilename);
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new StorageOperationException("Stored file is not readable", null);
            }
            return resource;
        } catch (IOException exception) {
            throw new StorageOperationException("Failed to load stored file", exception);
        }
    }

    public boolean exists(String storageFilename) {
        return Files.exists(resolve(storageFilename));
    }

    public void delete(String storageFilename) {
        try {
            Files.deleteIfExists(resolve(storageFilename));
        } catch (IOException exception) {
            throw new StorageOperationException("Failed to delete stored file", exception);
        }
    }

    private void initialize() {
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            throw new StorageOperationException("Failed to initialize storage directory", exception);
        }
    }

    private Path resolve(String storageFilename) {
        Path target = rootDirectory.resolve(storageFilename).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new StorageOperationException("Resolved path is outside the storage directory", null);
        }
        return target;
    }
}
