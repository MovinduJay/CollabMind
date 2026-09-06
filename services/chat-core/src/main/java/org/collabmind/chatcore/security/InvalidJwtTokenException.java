package org.collabmind.chatcore.security;

public class InvalidJwtTokenException extends RuntimeException {

    public InvalidJwtTokenException(String message) {
        super(message);
    }
}
