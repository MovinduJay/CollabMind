package org.collabmind.ai.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationError(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return new ApiErrorResponse(
                "VALIDATION_ERROR",
                message,
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                Instant.now()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return new ApiErrorResponse(
                "INVALID_REQUEST_BODY",
                "Request body is invalid or contains unsupported values",
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                Instant.now()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return new ApiErrorResponse(
                "BAD_REQUEST",
                safeMessage(exception),
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                Instant.now()
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleIllegalState(
            IllegalStateException exception,
            HttpServletRequest request
    ) {
        log.error("AI orchestrator configuration/state error", exception);

        return new ApiErrorResponse(
                "AI_ORCHESTRATOR_STATE_ERROR",
                safeMessage(exception),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                request.getRequestURI(),
                Instant.now()
        );
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleDataAccessError(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        log.error("AI audit log persistence error", exception);

        return new ApiErrorResponse(
                "AI_AUDIT_PERSISTENCE_ERROR",
                "Failed to access AI audit log storage",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                request.getRequestURI(),
                Instant.now()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpectedError(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected AI orchestrator error", exception);

        return new ApiErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Unexpected error in AI orchestrator",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                request.getRequestURI(),
                Instant.now()
        );
    }

    private String safeMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getMessage();
    }
}
