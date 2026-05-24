package com.foundflow.photo.storage;

public interface PhotoStorage {

    String store(PhotoData photo);

    PhotoData retrieve(String photoKey);

    void delete(String photoKey);
}
