package com.foundflow.founditem.config;

import com.foundflow.photo.storage.FileSystemPhotoStorage;
import com.foundflow.photo.storage.MinioPhotoStorage;
import com.foundflow.photo.storage.PhotoStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Locale;

@Configuration
public class PhotoStorageConfig {

    private static final String DOMAIN = "found-items";

    @Bean
    public PhotoStorage photoStorage(
            @Value("${photo-storage.provider:${PHOTO_STORAGE_PROVIDER:local}}") String provider,
            @Value("${photo-storage.local.root:${PHOTO_STORAGE_LOCAL_ROOT:build/photo-storage}}") String localRoot,
            @Value("${photo-storage.endpoint:${PHOTO_STORAGE_ENDPOINT:http://localhost:9000}}") String endpoint,
            @Value("${photo-storage.public-endpoint:${PHOTO_STORAGE_PUBLIC_ENDPOINT:${photo-storage.endpoint:${PHOTO_STORAGE_ENDPOINT:http://localhost:9000}}}}") String publicEndpoint,
            @Value("${photo-storage.access-key:${PHOTO_STORAGE_ACCESS_KEY:}}") String accessKey,
            @Value("${photo-storage.secret-key:${PHOTO_STORAGE_SECRET_KEY:}}") String secretKey,
            @Value("${photo-storage.bucket:${PHOTO_STORAGE_BUCKET:foundflow-found-photos}}") String bucketName
    ) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "minio" -> MinioPhotoStorage.create(endpoint, publicEndpoint, accessKey, secretKey, bucketName, DOMAIN);
            case "local" -> new FileSystemPhotoStorage(Path.of(localRoot), DOMAIN);
            default -> throw new IllegalArgumentException("Unsupported photo storage provider: " + provider);
        };
    }
}
