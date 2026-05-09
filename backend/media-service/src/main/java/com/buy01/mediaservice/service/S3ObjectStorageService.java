package com.buy01.mediaservice.service;

import com.buy01.mediaservice.exception.StorageOperationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final AtomicBoolean bucketInitialized = new AtomicBoolean(false);

    public S3ObjectStorageService(
            @Value("${app.storage.s3.endpoint}") String endpoint,
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.access-key}") String accessKey,
            @Value("${app.storage.s3.secret-key}") String secretKey,
            @Value("${app.storage.s3.bucket}") String bucketName
    ) {
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Override
    public void store(String storageFilename, byte[] bytes) {
        ensureBucketExists();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageFilename)
                            .build(),
                    RequestBody.fromBytes(bytes)
            );
        } catch (S3Exception exception) {
            throw new StorageOperationException("Failed to store file", exception);
        }
    }

    @Override
    public Resource loadAsResource(String storageFilename) {
        ensureBucketExists();
        try {
            byte[] bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageFilename)
                            .build()
            ).asByteArray();

            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return storageFilename;
                }
            };
        } catch (NoSuchKeyException exception) {
            throw new StorageOperationException("Stored file is not readable", exception);
        } catch (S3Exception exception) {
            throw new StorageOperationException("Failed to load stored file", exception);
        }
    }

    @Override
    public boolean exists(String storageFilename) {
        ensureBucketExists();
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageFilename)
                            .build()
            );
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new StorageOperationException("Failed to determine if stored file exists", exception);
        }
    }

    @Override
    public void delete(String storageFilename) {
        ensureBucketExists();
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(storageFilename)
                            .build()
            );
        } catch (S3Exception exception) {
            throw new StorageOperationException("Failed to delete stored file", exception);
        }
    }

    private void ensureBucketExists() {
        if (bucketInitialized.get()) {
            return;
        }

        synchronized (bucketInitialized) {
            if (bucketInitialized.get()) {
                return;
            }

            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            } catch (NoSuchBucketException exception) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            } catch (S3Exception exception) {
                if (exception.statusCode() == 404) {
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                } else {
                    throw new StorageOperationException("Failed to initialize object storage bucket", exception);
                }
            }

            bucketInitialized.set(true);
        }
    }
}
