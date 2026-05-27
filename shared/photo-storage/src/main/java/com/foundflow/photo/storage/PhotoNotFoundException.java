package com.foundflow.photo.storage;

public class PhotoNotFoundException extends PhotoStorageException {

    public PhotoNotFoundException(String photoKey) {
        super("Photo not found: " + photoKey);
    }

    public PhotoNotFoundException(String photoKey, Throwable cause) {
        super("Photo not found: " + photoKey, cause);
    }
}
