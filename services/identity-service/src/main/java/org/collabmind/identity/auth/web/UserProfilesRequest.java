package org.collabmind.identity.auth.web;

import java.util.List;
import java.util.UUID;

public record UserProfilesRequest(
        List<UUID> userIds
) {
}
