package org.collabmind.identity.auth.web;

import org.collabmind.identity.auth.infrastructure.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserAccountRepository userAccountRepository;

    public UserProfileController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/{userId}")
    public UserProfileResponse getUserProfile(@PathVariable UUID userId) {
        return userAccountRepository
                .findById(userId)
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User profile not found"
                ));
    }

    @PostMapping("/profiles")
    public List<UserProfileResponse> getUserProfiles(@RequestBody UserProfilesRequest request) {
        if (request.userIds() == null || request.userIds().isEmpty()) {
            return List.of();
        }

        return userAccountRepository
                .findAllById(request.userIds())
                .stream()
                .map(UserProfileResponse::from)
                .toList();
    }
}
