package org.collabmind.chatcore.conversation.web;

import jakarta.validation.Valid;
import org.collabmind.chatcore.conversation.application.ConversationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ConversationResponse createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            Authentication authentication
    ) {
        return conversationService.createConversation(
                request.name(),
                authenticatedUserId(authentication)
        );
    }

    @GetMapping
    public List<ConversationResponse> listMyConversations(Authentication authentication) {
        return conversationService.listMyConversations(
                authenticatedUserId(authentication)
        );
    }

    @PostMapping("/{conversationId}/members")
    public ConversationResponse joinConversation(
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {
        return conversationService.joinConversation(
                conversationId,
                authenticatedUserId(authentication)
        );
    }

    @GetMapping("/{conversationId}/members")
    public List<ConversationMemberResponse> listMembers(
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {
        return conversationService.listMembers(
                conversationId,
                authenticatedUserId(authentication)
        );
    }

    @GetMapping("/{conversationId}/members/{userId}/exists")
    public ConversationMembershipResponse checkMembership(
            @PathVariable UUID conversationId,
            @PathVariable UUID userId,
            Authentication authentication
    ) {
        UUID authenticatedUserId = authenticatedUserId(authentication);

        if (!authenticatedUserId.equals(userId)) {
            throw new IllegalArgumentException("Cannot check membership for another user");
        }

        return conversationService.checkMembership(conversationId, userId);
    }

    private UUID authenticatedUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
