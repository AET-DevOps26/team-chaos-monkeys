package com.foundflow.photo.storage;

import java.net.URI;
import java.time.Duration;

public interface PhotoStorage {

    String store(PhotoData photo);

    PhotoData retrieve(String photoKey);

    URI signedUrl(String photoKey, Duration ttl);

    void delete(String photoKey);
}
