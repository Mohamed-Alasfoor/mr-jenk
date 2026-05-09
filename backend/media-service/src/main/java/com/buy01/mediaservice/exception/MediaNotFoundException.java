package com.buy01.mediaservice.exception;

public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException(String mediaId) {
        super("Media not found: " + mediaId);
    }
}
