package org.collabmind.chatcore.messaging.application;

import org.collabmind.chatcore.common.exception.ConversationNotFoundException;
import org.collabmind.chatcore.common.exception.UserNotConversationMemberException;
import org.collabmind.chatcore.conversation.domain.Conversation;
import org.collabmind.chatcore.conversation.infrastructure.ConversationRepository;
import org.collabmind.chatcore.membership.infrastructure.ConversationMemberRepository;
import org.collabmind.chatcore.messaging.domain.Message;
import org.collabmind.chatcore.messaging.infrastructure.MessageRepository;
import org.collabmind.chatcore.messaging.web.MessageResponse;
import org.collabmind.chatcore.messaging.web.SaveAiMessageRequest;
import org.collabmind.chatcore.messaging.web.SendMessageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public MessageResponse sendMessage(
            UUID conversationId,
            UUID authenticatedUserId,
            SendMessageRequest request
    ) {
        return messageRepository
                .findBySenderIdAndClientMessageId(
                        authenticatedUserId,
                        request.clientMessageId()
                )
                .map(MessageResponse::from)
                .orElseGet(() -> createNewUserMessage(
                        conversationId,
                        authenticatedUserId,
                        request
                ));
    }

    private MessageResponse createNewUserMessage(
            UUID conversationId,
            UUID authenticatedUserId,
            SendMessageRequest request
    ) {
        validateMembership(conversationId, authenticatedUserId);

        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        long sequenceNumber = conversation.allocateNextSequence();

        Message message = Message.userMessage(
                conversationId,
                authenticatedUserId,
                request.clientMessageId(),
                sequenceNumber,
                request.content()
        );

        Message savedMessage = messageRepository.save(message);

        return MessageResponse.from(savedMessage);
    }

    @Transactional
    public MessageResponse saveAiMessage(
            UUID conversationId,
            UUID authenticatedUserId,
            SaveAiMessageRequest request
    ) {
        return messageRepository
                .findBySenderIdAndClientMessageId(
                        Message.AI_SYSTEM_SENDER_ID,
                        request.clientMessageId()
                )
                .map(MessageResponse::from)
                .orElseGet(() -> createNewAiMessage(
                        conversationId,
                        authenticatedUserId,
                        request
                ));
    }

    private MessageResponse createNewAiMessage(
            UUID conversationId,
            UUID authenticatedUserId,
            SaveAiMessageRequest request
    ) {
        validateMembership(conversationId, authenticatedUserId);

        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        long sequenceNumber = conversation.allocateNextSequence();

        Message message = Message.aiMessage(
                conversationId,
                request.clientMessageId(),
                sequenceNumber,
                request.agentType(),
                request.sourceMessageId(),
                request.content()
        );

        Message savedMessage = messageRepository.save(message);

        return MessageResponse.from(savedMessage);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessagesAfter(
            UUID conversationId,
            UUID authenticatedUserId,
            long afterSequence,
            int limit
    ) {
        validateMembership(conversationId, authenticatedUserId);

        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));

        return messageRepository.findMessagesAfter(
                        conversationId,
                        afterSequence,
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(MessageResponse::from)
                .toList();
    }

    private void validateMembership(
            UUID conversationId,
            UUID userId
    ) {
        boolean member = memberRepository.existsByConversationIdAndUserId(
                conversationId,
                userId
        );

        if (!member) {
            throw new UserNotConversationMemberException(userId, conversationId);
        }
    }
}
