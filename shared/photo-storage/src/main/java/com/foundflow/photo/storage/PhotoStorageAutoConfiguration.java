package com.foundflow.photo.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.Locale;

@AutoConfiguration
public class PhotoStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PhotoStorage.class)
    public PhotoStorage photoStorage(
            @Value("${photo-storage.provider:local}") String provider,
            @Value("${photo-storage.domain}") String domain,
            @Value("${photo-storage.local.root:build/photo-storage}") String localRoot,
            @Value("${photo-storage.endpoint:http://localhost:9000}") String endpoint,
            @Value("${photo-storage.public-endpoint:${photo-storage.endpoint:http://localhost:9000}}") String publicEndpoint,
            @Value("${photo-storage.access-key:}") String accessKey,
            @Value("${photo-storage.secret-key:}") String secretKey,
            @Value("${photo-storage.bucket:}") String bucket,
            @Value("${photo-storage.connection-string:}") String connectionString,
            @Value("${photo-storage.container:}") String container
    ) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "minio" -> MinioPhotoStorage.create(endpoint, publicEndpoint, accessKey, secretKey, bucket, domain);
            case "azure" -> AzurePhotoStorage.create(connectionString, container, domain);
            case "local" -> new FileSystemPhotoStorage(Path.of(localRoot), domain);
            default -> throw new IllegalArgumentException("Unsupported photo storage provider: " + provider);
        };
    }
}
