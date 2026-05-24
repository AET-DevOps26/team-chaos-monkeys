package com.foundflow.photo.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import okhttp3.Headers;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class MinioPhotoStorage implements PhotoStorage {

    private static final String DEFAULT_REGION = "us-east-1";

    private final MinioClient minioClient;
    private final MinioClient presignClient;
    private final String bucketName;
    private final PhotoKeyFactory keyFactory;

    public static MinioPhotoStorage create(
            String endpoint,
            String publicEndpoint,
            String accessKey,
            String secretKey,
            String bucketName,
            String domain
    ) {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .region(DEFAULT_REGION)
                .credentials(accessKey, secretKey)
                .build();
        MinioClient presignClient = MinioClient.builder()
                .endpoint(publicEndpoint)
                .region(DEFAULT_REGION)
                .credentials(accessKey, secretKey)
                .build();

        return new MinioPhotoStorage(client, presignClient, bucketName, domain);
    }

    MinioPhotoStorage(MinioClient minioClient, MinioClient presignClient, String bucketName, String domain) {
        this.minioClient = minioClient;
        this.presignClient = presignClient;
        this.bucketName = bucketName;
        this.keyFactory = new PhotoKeyFactory(domain, Clock.systemUTC());
        ensureBucketExists();
    }

    @Override
    public String store(PhotoData photo) {
        String photoKey = keyFactory.createKey(photo.contentType());
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(photoKey)
                    .contentType(photo.contentType())
                    .stream(photo.content(), photo.sizeBytes(), -1L)
                    .build());
            return photoKey;
        } catch (Exception exception) {
            throw new PhotoStorageException("Could not store photo.", exception);
        }
    }

    @Override
    public PhotoData retrieve(String photoKey) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(photoKey)
                    .build());
            Headers headers = response.headers();
            String contentType = headerOrFallback(headers, "Content-Type", keyFactory.contentTypeFor(photoKey));
            long sizeBytes = parseLength(headers.get("Content-Length"));
            return new PhotoData(response, contentType, sizeBytes);
        } catch (ErrorResponseException exception) {
            if (isMissingObject(exception)) {
                throw new PhotoNotFoundException(photoKey, exception);
            }
            throw new PhotoStorageException("Could not retrieve photo.", exception);
        } catch (Exception exception) {
            throw new PhotoStorageException("Could not retrieve photo.", exception);
        }
    }

    private static String headerOrFallback(Headers headers, String name, String fallback) {
        String value = headers.get(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static long parseLength(String value) {
        if (value == null || value.isBlank()) {
            return -1L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    @Override
    public URI signedUrl(String photoKey, Duration ttl) {
        validateTtl(ttl);
        try {
            ensureObjectExists(photoKey);
            return URI.create(presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(photoKey)
                    .expiry(Math.toIntExact(ttl.toSeconds()), TimeUnit.SECONDS)
                    .build()));
        } catch (PhotoNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PhotoStorageException("Could not create signed photo URL.", exception);
        }
    }

    @Override
    public void delete(String photoKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(photoKey)
                    .build());
        } catch (Exception exception) {
            throw new PhotoStorageException("Could not delete photo.", exception);
        }
    }

    private void ensureObjectExists(String photoKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(photoKey)
                    .build());
        } catch (ErrorResponseException exception) {
            if (isMissingObject(exception)) {
                throw new PhotoNotFoundException(photoKey, exception);
            }
            throw new PhotoStorageException("Could not stat photo.", exception);
        } catch (Exception exception) {
            throw new PhotoStorageException("Could not stat photo.", exception);
        }
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new PhotoStorageException("Signed URL TTL must be positive.");
        }
        if (ttl.compareTo(Duration.ofDays(7)) > 0) {
            throw new PhotoStorageException("Signed URL TTL must not exceed 7 days.");
        }
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
            }
        } catch (Exception exception) {
            throw new PhotoStorageException("Could not prepare photo bucket.", exception);
        }
    }

    private boolean isMissingObject(ErrorResponseException exception) {
        String code = exception.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code);
    }
}
