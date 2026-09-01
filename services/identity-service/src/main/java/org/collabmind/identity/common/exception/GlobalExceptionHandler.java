package org.collabmind.identity.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleInvalidCredentials(InvalidCredentialsException exception) {
        return new ApiErrorResponse(
                "INVALID_CREDENTIALS",
                exception.getMessage(),
                HttpStatus.UNAUTHORIZED.value(),
                Instant.now()
        );
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return new ApiErrorResponse(
                "EMAIL_ALREADY_REGISTERED",
                exception.getMessage(),
                HttpStatus.CONFLICT.value(),
                Instant.now()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return new ApiErrorResponse(
                "BAD_REQUEST",
                exception.getMessage(),
                HttpStatus.BAD_REQUEST.value(),
                Instant.now()
        );
    }
}
