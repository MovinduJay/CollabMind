package org.collabmind.identity.auth.web;

import org.collabmind.identity.auth.domain.UserAccount;

import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String displayName
) {
    public static UserProfileResponse from(UserAccount userAccount) {
        return new UserProfileResponse(
                userAccount.getId(),
                userAccount.getDisplayName()
        );
    }
}
