package org.collabmind.identity.auth.web;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String displayName,
        String email,
        String accessToken
) {
}
