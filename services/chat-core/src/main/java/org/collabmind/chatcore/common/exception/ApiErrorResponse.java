package org.collabmind.chatcore.common.exception;

import java.time.Instant;

public record ApiErrorResponse(
        String error,
        String message,
        int status,
        Instant timestamp
) {
}
