package org.collabmind.chatcore.conversation.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.collabmind.chatcore.conversation.application.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createConversation(
            @Valid @RequestBody CreateConversationRequest request
    ) {
        return conversationService.createConversation(request);
    }

    @PostMapping("/{conversationId}/members")
    public ConversationResponse joinConversation(
            @PathVariable UUID conversationId,
            @Valid @RequestBody JoinConversationRequest request
    ) {
        return conversationService.joinConversation(conversationId, request.userId());
    }

    public record JoinConversationRequest(
            @NotNull(message = "User ID is required")
            UUID userId
    ) {
    }
}
