package org.collabmind.chatcore.messaging.web;

import jakarta.validation.Valid;
import org.collabmind.chatcore.messaging.application.MessageService;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return messageService.sendMessage(conversationId, request);
    }

    @GetMapping
    public List<MessageResponse> getMessagesAfter(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return messageService.getMessagesAfter(conversationId, afterSequence, limit);
    }
}
