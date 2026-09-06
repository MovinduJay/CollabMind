package org.collabmind.chatcore.messaging.web;

import jakarta.validation.Valid;
import org.collabmind.chatcore.messaging.application.MessageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public MessageResponse sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication
    ) {
        return messageService.sendMessage(
                conversationId,
                authenticatedUserId(authentication),
                request
        );
    }

    @PostMapping("/ai")
    public MessageResponse saveAiMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SaveAiMessageRequest request,
            Authentication authentication
    ) {
        return messageService.saveAiMessage(
                conversationId,
                authenticatedUserId(authentication),
                request
        );
    }

    @GetMapping
    public List<MessageResponse> getMessagesAfter(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        return messageService.getMessagesAfter(
                conversationId,
                authenticatedUserId(authentication),
                afterSequence,
                limit
        );
    }

    private UUID authenticatedUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
