package org.collabmind.chatcore.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleConversationNotFound(ConversationNotFoundException exception) {
        return new ApiErrorResponse(
                "CONVERSATION_NOT_FOUND",
                exception.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                Instant.now()
        );
    }

    @ExceptionHandler(UserNotConversationMemberException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleUserNotMember(UserNotConversationMemberException exception) {
        return new ApiErrorResponse(
                "USER_NOT_CONVERSATION_MEMBER",
                exception.getMessage(),
                HttpStatus.FORBIDDEN.value(),
                Instant.now()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationError(MethodArgumentNotValidException exception) {
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
