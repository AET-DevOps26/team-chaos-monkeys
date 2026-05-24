package com.foundflow.photo.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

public class FileSystemPhotoStorage implements PhotoStorage {

    private final Path rootDirectory;
    private final PhotoKeyFactory keyFactory;

    public FileSystemPhotoStorage(Path rootDirectory, String domain) {
        this.rootDirectory = rootDirectory;
        this.keyFactory = new PhotoKeyFactory(domain, Clock.systemUTC());
    }

    @Override
    public String store(PhotoData photo) {
        String photoKey = keyFactory.createKey(photo.contentType());
        Path target = pathFor(photoKey);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream content = photo.content()) {
                Files.copy(content, target);
            }
            return photoKey;
        } catch (IOException exception) {
            throw new PhotoStorageException("Could not store photo.", exception);
        }
    }

    @Override
    public PhotoData retrieve(String photoKey) {
        Path path = pathFor(photoKey);
        if (!Files.exists(path)) {
            throw new PhotoNotFoundException(photoKey);
        }

        try {
            return new PhotoData(
                    Files.newInputStream(path),
                    keyFactory.contentTypeFor(photoKey),
                    Files.size(path)
            );
        } catch (IOException exception) {
            throw new PhotoStorageException("Could not retrieve photo.", exception);
        }
    }

    @Override
    public void delete(String photoKey) {
        try {
            Files.deleteIfExists(pathFor(photoKey));
        } catch (IOException exception) {
            throw new PhotoStorageException("Could not delete photo.", exception);
        }
    }

    private Path pathFor(String photoKey) {
        Path resolvedPath = rootDirectory.resolve(photoKey).normalize();
        if (!resolvedPath.startsWith(rootDirectory.normalize())) {
            throw new PhotoStorageException("Invalid photo key.");
        }
        return resolvedPath;
    }
}
